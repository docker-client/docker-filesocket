package main

import (
	"fmt"
	"log"
	"runtime"
	"sync"
	"syscall"
	"time"
	"unsafe"
)

// Windows API constants and types
const (
	PIPE_ACCESS_DUPLEX                   = 0x00000003
	PIPE_TYPE_BYTE                       = 0x00000000
	PIPE_READMODE_BYTE                   = 0x00000000
	PIPE_WAIT                            = 0x00000000
	GENERIC_READ                         = 0x80000000
	GENERIC_WRITE                        = 0x40000000
	OPEN_EXISTING                        = 3
	FILE_FLAG_OVERLAPPED                 = 0x40000000
	INVALID_HANDLE_VALUE                 = ^uintptr(0)
	ERROR_IO_PENDING                     = 997
	ERROR_PIPE_CONNECTED                 = 535
	ERROR_PIPE_BUSY                      = 231
	ERROR_BROKEN_PIPE                    = 109
	ERROR_OPERATION_ABORTED              = 995
	INFINITE                             = 0xFFFFFFFF
	FILE_SKIP_COMPLETION_PORT_ON_SUCCESS = 0x1
	FILE_SKIP_SET_EVENT_ON_HANDLE        = 0x2
)

type HANDLE uintptr
type OVERLAPPED struct {
	Internal     uintptr
	InternalHigh uintptr
	Offset       uint32
	OffsetHigh   uint32
	HEvent       HANDLE
}

var (
	kernel32                               = syscall.NewLazyDLL("kernel32.dll")
	procCreateNamedPipe                    = kernel32.NewProc("CreateNamedPipeW")
	procConnectNamedPipe                   = kernel32.NewProc("ConnectNamedPipe")
	procCreateFile                         = kernel32.NewProc("CreateFileW")
	procReadFile                           = kernel32.NewProc("ReadFile")
	procWriteFile                          = kernel32.NewProc("WriteFile")
	procCloseHandle                        = kernel32.NewProc("CloseHandle")
	procDisconnectNamedPipe                = kernel32.NewProc("DisconnectNamedPipe")
	procGetLastError                       = kernel32.NewProc("GetLastError")
	procCreateIoCompletionPort             = kernel32.NewProc("CreateIoCompletionPort")
	procGetQueuedCompletionStatus          = kernel32.NewProc("GetQueuedCompletionStatus")
	procCancelIoEx                         = kernel32.NewProc("CancelIoEx")
	procSetFileCompletionNotificationModes = kernel32.NewProc("SetFileCompletionNotificationModes")
)

// I/O Completion result
type ioResult struct {
	bytes uint32
	err   error
}

// I/O Operation tracking
type ioOperation struct {
	overlapped OVERLAPPED
	ch         chan ioResult
}

// Win32File represents a file handle with I/O completion port support
type Win32File struct {
	handle         HANDLE
	completionPort HANDLE
	wg             sync.WaitGroup
	closing        bool
	closingMutex   sync.RWMutex
	useAsync       bool // Whether to use async I/O or sync I/O
}

// Global completion port and processor
var (
	globalCompletionPort HANDLE
	initOnce             sync.Once
)

func initCompletionPort() {
	ret, _, _ := procCreateIoCompletionPort.Call(
		uintptr(INVALID_HANDLE_VALUE), // FileHandle - use invalid handle for initial port
		0,                             // ExistingCompletionPort
		0,                             // CompletionKey
		0xffffffff,                    // NumberOfConcurrentThreads - unlimited
	)
	globalCompletionPort = HANDLE(ret)

	if globalCompletionPort == 0 || globalCompletionPort == HANDLE(INVALID_HANDLE_VALUE) {
		panic("Failed to create I/O completion port")
	}

	// Start completion processor goroutine
	go completionProcessor()
	log.Println("[DEBUG_LOG] I/O Completion Port initialized")
}

func completionProcessor() {
	log.Println("[DEBUG_LOG] Completion processor started")
	for {
		var bytes uint32
		var key uintptr
		var overlappedPtr uintptr

		ret, _, _ := procGetQueuedCompletionStatus.Call(
			uintptr(globalCompletionPort),
			uintptr(unsafe.Pointer(&bytes)),
			uintptr(unsafe.Pointer(&key)),
			uintptr(unsafe.Pointer(&overlappedPtr)),
			INFINITE,
		)

		if overlappedPtr == 0 {
			// No completion packet or error
			continue
		}

		// Convert overlapped pointer back to ioOperation
		op := (*ioOperation)(unsafe.Pointer(overlappedPtr))

		var err error
		if ret == 0 {
			errno, _, _ := procGetLastError.Call()
			err = syscall.Errno(errno)
		}

		// Send result to waiting goroutine
		select {
		case op.ch <- ioResult{bytes: bytes, err: err}:
		default:
			// Channel might be closed, ignore
		}
	}
}

func NewWin32File(handle HANDLE) *Win32File {
	initOnce.Do(initCompletionPort)

	// Try to associate handle with completion port (client-side pipes work, server-side may not)
	ret, _, _ := procCreateIoCompletionPort.Call(
		uintptr(handle),
		uintptr(globalCompletionPort),
		0,          // CompletionKey (not used)
		0xffffffff, // NumberOfConcurrentThreads - unlimited like go-winio
	)

	useAsync := ret != 0
	if useAsync {
		// Set file completion notification modes (like go-winio)
		ret, _, _ = procSetFileCompletionNotificationModes.Call(
			uintptr(handle),
			FILE_SKIP_COMPLETION_PORT_ON_SUCCESS|FILE_SKIP_SET_EVENT_ON_HANDLE,
		)

		if ret == 0 {
			log.Printf("[DEBUG_LOG] Warning: SetFileCompletionNotificationModes failed")
			useAsync = false
		}
	}

	if !useAsync {
		log.Printf("[DEBUG_LOG] Using synchronous I/O for handle %x", handle)
	}

	return &Win32File{
		handle:         handle,
		completionPort: globalCompletionPort,
		useAsync:       useAsync,
	}
}

func (f *Win32File) Read(buffer []byte) (int, error) {
	f.closingMutex.RLock()
	if f.closing {
		f.closingMutex.RUnlock()
		return 0, fmt.Errorf("file is closing")
	}
	f.wg.Add(1)
	f.closingMutex.RUnlock()
	defer f.wg.Done()

	if !f.useAsync {
		// Use synchronous I/O
		var bytesRead uint32
		ret, _, _ := procReadFile.Call(
			uintptr(f.handle),
			uintptr(unsafe.Pointer(&buffer[0])),
			uintptr(len(buffer)),
			uintptr(unsafe.Pointer(&bytesRead)),
			0, // No overlapped structure for sync I/O
		)

		if ret != 0 {
			return int(bytesRead), nil
		}

		errno, _, _ := procGetLastError.Call()
		if errno == 0 {
			return 0, nil // No error, just zero bytes read
		}
		return 0, syscall.Errno(errno)
	}

	// Use asynchronous I/O
	op := &ioOperation{
		ch: make(chan ioResult, 1),
	}

	var bytesRead uint32
	ret, _, _ := procReadFile.Call(
		uintptr(f.handle),
		uintptr(unsafe.Pointer(&buffer[0])),
		uintptr(len(buffer)),
		uintptr(unsafe.Pointer(&bytesRead)),
		uintptr(unsafe.Pointer(&op.overlapped)),
	)

	if ret != 0 {
		// Synchronous completion
		return int(bytesRead), nil
	}

	errno, _, _ := procGetLastError.Call()
	if errno != ERROR_IO_PENDING {
		if errno == 0 {
			return 0, nil // Success, but zero bytes read
		}
		return 0, syscall.Errno(errno)
	}

	// Wait for async completion
	result := <-op.ch
	runtime.KeepAlive(op)

	if result.err != nil {
		if result.err == syscall.Errno(ERROR_BROKEN_PIPE) {
			return 0, fmt.Errorf("EOF")
		}
		return 0, result.err
	}

	return int(result.bytes), nil
}

func (f *Win32File) Write(buffer []byte) (int, error) {
	f.closingMutex.RLock()
	if f.closing {
		f.closingMutex.RUnlock()
		return 0, fmt.Errorf("file is closing")
	}
	f.wg.Add(1)
	f.closingMutex.RUnlock()
	defer f.wg.Done()

	if !f.useAsync {
		// Use synchronous I/O
		var bytesWritten uint32
		ret, _, _ := procWriteFile.Call(
			uintptr(f.handle),
			uintptr(unsafe.Pointer(&buffer[0])),
			uintptr(len(buffer)),
			uintptr(unsafe.Pointer(&bytesWritten)),
			0, // No overlapped structure for sync I/O
		)

		if ret != 0 {
			return int(bytesWritten), nil
		}

		errno, _, _ := procGetLastError.Call()
		if errno == 0 {
			return 0, nil // No error, just zero bytes written
		}
		return 0, syscall.Errno(errno)
	}

	// Use asynchronous I/O
	op := &ioOperation{
		ch: make(chan ioResult, 1),
	}

	var bytesWritten uint32
	ret, _, _ := procWriteFile.Call(
		uintptr(f.handle),
		uintptr(unsafe.Pointer(&buffer[0])),
		uintptr(len(buffer)),
		uintptr(unsafe.Pointer(&bytesWritten)),
		uintptr(unsafe.Pointer(&op.overlapped)),
	)

	if ret != 0 {
		// Synchronous completion
		return int(bytesWritten), nil
	}

	errno, _, _ := procGetLastError.Call()
	if errno != ERROR_IO_PENDING {
		if errno == 0 {
			return 0, nil // Success, but zero bytes written
		}
		return 0, syscall.Errno(errno)
	}

	// Wait for async completion
	result := <-op.ch
	runtime.KeepAlive(op)

	if result.err != nil {
		return 0, result.err
	}

	return int(result.bytes), nil
}

func (f *Win32File) Close() error {
	f.closingMutex.Lock()
	f.closing = true
	f.closingMutex.Unlock()

	// Wait for all operations to complete
	f.wg.Wait()

	ret, _, _ := procCloseHandle.Call(uintptr(f.handle))
	if ret == 0 {
		errno, _, _ := procGetLastError.Call()
		return syscall.Errno(errno)
	}
	return nil
}

// Helper functions for pipe creation and connection
func CreateNamedPipe(name string) (HANDLE, error) {
	namePtr, _ := syscall.UTF16PtrFromString(name)
	ret, _, _ := procCreateNamedPipe.Call(
		uintptr(unsafe.Pointer(namePtr)),
		PIPE_ACCESS_DUPLEX,
		PIPE_TYPE_BYTE|PIPE_READMODE_BYTE|PIPE_WAIT,
		1,    // nMaxInstances
		4096, // nOutBufferSize
		4096, // nInBufferSize
		0,    // nDefaultTimeOut
		0,    // lpSecurityAttributes
	)

	handle := HANDLE(ret)
	if handle == HANDLE(INVALID_HANDLE_VALUE) {
		errno, _, _ := procGetLastError.Call()
		return 0, syscall.Errno(errno)
	}
	return handle, nil
}

func ConnectNamedPipe(handle HANDLE) error {
	ret, _, _ := procConnectNamedPipe.Call(uintptr(handle), 0)
	if ret == 0 {
		errno, _, _ := procGetLastError.Call()
		if errno != ERROR_PIPE_CONNECTED {
			return syscall.Errno(errno)
		}
	}
	return nil
}

func ConnectToPipe(name string) (HANDLE, error) {
	namePtr, _ := syscall.UTF16PtrFromString(name)
	ret, _, _ := procCreateFile.Call(
		uintptr(unsafe.Pointer(namePtr)),
		GENERIC_READ|GENERIC_WRITE,
		0,
		0,
		OPEN_EXISTING,
		FILE_FLAG_OVERLAPPED,
		0,
	)

	handle := HANDLE(ret)
	if handle == HANDLE(INVALID_HANDLE_VALUE) {
		errno, _, _ := procGetLastError.Call()
		return 0, syscall.Errno(errno)
	}
	return handle, nil
}

// Test functions using the winio-style approach
func testConcurrentConnections() error {
	log.Println("[DEBUG_LOG] --- Test: Concurrent Multiple Pipe Connections (winio-style) ---")

	numPipes := 3
	serverDone := make(chan bool, numPipes)

	// Start servers
	for i := 0; i < numPipes; i++ {
		go func(pipeIndex int) {
			pipeName := fmt.Sprintf(`\\.\pipe\winio_style_test_%d`, pipeIndex)

			handle, err := CreateNamedPipe(pipeName)
			if err != nil {
				log.Printf("[DEBUG_LOG] Server %d: Failed to create pipe: %v", pipeIndex, err)
				serverDone <- false
				return
			}
			defer procCloseHandle.Call(uintptr(handle))

			log.Printf("[DEBUG_LOG] Server %d: Created pipe %s", pipeIndex, pipeName)

			err = ConnectNamedPipe(handle)
			if err != nil {
				log.Printf("[DEBUG_LOG] Server %d: ConnectNamedPipe failed: %v", pipeIndex, err)
				serverDone <- false
				return
			}

			log.Printf("[DEBUG_LOG] Server %d: Client connected", pipeIndex)

			// Use Win32File for I/O
			file := NewWin32File(handle)

			// Read message
			buffer := make([]byte, 1024)
			n, err := file.Read(buffer)
			if err != nil {
				log.Printf("[DEBUG_LOG] Server %d: Read failed: %v", pipeIndex, err)
				serverDone <- false
				return
			}

			message := string(buffer[:n])
			log.Printf("[DEBUG_LOG] Server %d received: %s", pipeIndex, message)

			// Send response
			response := fmt.Sprintf("Server %d echo: %s", pipeIndex, message)
			_, err = file.Write([]byte(response))
			if err != nil {
				log.Printf("[DEBUG_LOG] Server %d: Write failed: %v", pipeIndex, err)
				serverDone <- false
				return
			}

			log.Printf("[DEBUG_LOG] Server %d: Sent response", pipeIndex)
			serverDone <- true
		}(i)
	}

	// Wait for servers to start
	time.Sleep(100 * time.Millisecond)

	// Start clients
	clientDone := make(chan bool, numPipes)
	for i := 0; i < numPipes; i++ {
		go func(clientIndex int) {
			pipeName := fmt.Sprintf(`\\.\pipe\winio_style_test_%d`, clientIndex)

			handle, err := ConnectToPipe(pipeName)
			if err != nil {
				log.Printf("[DEBUG_LOG] Client %d: Failed to connect: %v", clientIndex, err)
				clientDone <- false
				return
			}
			defer procCloseHandle.Call(uintptr(handle))

			log.Printf("[DEBUG_LOG] Client %d connected", clientIndex)

			// Use Win32File for I/O
			file := NewWin32File(handle)

			// Send message
			message := fmt.Sprintf("winio-style message from client %d!", clientIndex)
			_, err = file.Write([]byte(message))
			if err != nil {
				log.Printf("[DEBUG_LOG] Client %d: Write failed: %v", clientIndex, err)
				clientDone <- false
				return
			}

			// Read response
			buffer := make([]byte, 1024)
			n, err := file.Read(buffer)
			if err != nil {
				log.Printf("[DEBUG_LOG] Client %d: Read failed: %v", clientIndex, err)
				clientDone <- false
				return
			}

			response := string(buffer[:n])
			log.Printf("[DEBUG_LOG] Client %d received: %s", clientIndex, response)
			clientDone <- true
		}(i)
	}

	// Wait for all operations to complete
	successCount := 0
	for i := 0; i < numPipes*2; i++ { // servers + clients
		select {
		case success := <-serverDone:
			if success {
				successCount++
			}
		case success := <-clientDone:
			if success {
				successCount++
			}
		case <-time.After(10 * time.Second):
			return fmt.Errorf("timeout waiting for operations")
		}
	}

	if successCount == numPipes*2 {
		log.Println("[DEBUG_LOG] ✅ All concurrent connections completed successfully!")
		return nil
	}

	return fmt.Errorf("only %d/%d operations succeeded", successCount, numPipes*2)
}

func main() {
	log.Println("=== Windows Named Pipe winio-style Implementation Test ===")

	err := testConcurrentConnections()
	if err != nil {
		log.Printf("❌ Test failed: %v", err)
	} else {
		log.Println("✅ All tests passed!")
	}
}
