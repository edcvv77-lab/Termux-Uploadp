extends Node

signal termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int)
signal directory_picked(uri: String)
signal bridge_error(message: String)

var native_bridge: Object = null

func _ready() -> void:
    if Engine.has_singleton("CyberLifeBridge"):
        native_bridge = Engine.get_singleton("CyberLifeBridge")
        native_bridge.connect("termux_result", _on_native_termux_result)
        native_bridge.connect("directory_picked", _on_native_directory_picked)
        native_bridge.connect("bridge_error", _on_native_bridge_error)

func has_native_bridge() -> bool:
    return native_bridge != null

func is_termux_available() -> bool:
    if native_bridge == null:
        return false
    return bool(native_bridge.isTermuxAvailable())

func request_termux_permission() -> bool:
    if native_bridge == null:
        return false
    native_bridge.requestTermuxPermission()
    return true

func run_termux_command(command: String) -> Dictionary:
    command = command.strip_edges()
    if command.is_empty():
        return {"ok": false, "message": "Empty command."}
    if command.length() > 32768:
        return {"ok": false, "message": "Command is too long."}

    if native_bridge != null:
        var execution_id = int(native_bridge.runTermuxCommand(command))
        if execution_id >= 0:
            return {"ok": true, "execution_id": execution_id, "message": "Queued in Termux."}
        return {"ok": false, "message": "Termux command could not be started. Check permission and allow-external-apps."}

    return {
        "ok": false,
        "message": "[Desktop preview] Android Termux bridge is unavailable."
    }

func open_url(url: String) -> Dictionary:
    var safe_url := url.strip_edges()
    if safe_url.is_empty():
        return {"ok": false, "message": "Enter a URL first."}
    if not safe_url.begins_with("http://") and not safe_url.begins_with("https://"):
        safe_url = "https://" + safe_url

    if native_bridge != null:
        var opened = bool(native_bridge.openBrowser(safe_url))
        return {"ok": opened, "message": "Opened browser." if opened else "Could not open browser."}

    var err := OS.shell_open(safe_url)
    return {"ok": err == OK, "message": "Opened in the system browser." if err == OK else "Could not open URL."}

func pick_directory() -> Dictionary:
    if native_bridge != null:
        native_bridge.pickDirectory()
        return {"ok": true, "message": "Android folder picker opened."}

    return {"ok": false, "message": "[Desktop preview] Android folder picker is unavailable."}

func device_summary() -> String:
    if native_bridge != null:
        return str(native_bridge.deviceSummary())
    return "Platform: %s\nBridge: preview\nLocale: %s" % [OS.get_name(), TranslationServer.get_locale()]

func _on_native_termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int) -> void:
    termux_result.emit(execution_id, stdout, stderr, exit_code)

func _on_native_directory_picked(uri: String) -> void:
    directory_picked.emit(uri)

func _on_native_bridge_error(message: String) -> void:
    bridge_error.emit(message)
