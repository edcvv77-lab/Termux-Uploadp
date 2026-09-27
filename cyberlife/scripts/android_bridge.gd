extends Node

signal termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int)
signal directory_picked(uri: String)
signal download_started(file_name: String)
signal bridge_error(message: String)

var native_bridge: Object = null

func _ready() -> void:
    if Engine.has_singleton("CyberLifeBridge"):
        native_bridge = Engine.get_singleton("CyberLifeBridge")
        native_bridge.connect("termux_result", _on_native_termux_result)
        native_bridge.connect("directory_picked", _on_native_directory_picked)
        native_bridge.connect("download_started", _on_native_download_started)
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

    return {"ok": false, "message": "[Desktop preview] Android Termux bridge is unavailable."}

func open_url(url: String) -> Dictionary:
    var safe_url := _normalize_url(url)
    if safe_url.is_empty():
        return {"ok": false, "message": "Enter a URL first."}

    if native_bridge != null:
        var opened = bool(native_bridge.openBrowser(safe_url))
        return {"ok": opened, "message": "Opened external browser." if opened else "Could not open browser."}

    var err := OS.shell_open(safe_url)
    return {"ok": err == OK, "message": "Opened in the system browser." if err == OK else "Could not open URL."}

func open_in_app_browser(url: String) -> Dictionary:
    var safe_url := _normalize_url(url)
    if safe_url.is_empty():
        return {"ok": false, "message": "Enter a URL first."}

    if native_bridge != null:
        var opened = bool(native_bridge.showWebView(safe_url))
        return {"ok": opened, "message": "Browser opened inside CyberLife." if opened else "Could not open in-app browser."}

    return open_url(safe_url)

func pick_directory() -> Dictionary:
    if native_bridge != null:
        native_bridge.pickDirectory()
        return {"ok": true, "message": "Android folder picker opened."}
    return {"ok": false, "message": "[Desktop preview] Android folder picker is unavailable."}

func selected_directory_uri() -> String:
    if native_bridge == null:
        return ""
    return str(native_bridge.getSelectedDirectoryUri())

func list_selected_directory() -> Array:
    if native_bridge == null:
        return []
    var raw := str(native_bridge.listSelectedDirectory())
    var parsed = JSON.parse_string(raw)
    if typeof(parsed) == TYPE_ARRAY:
        return parsed
    return []

func open_document(uri: String) -> bool:
    if native_bridge == null or uri.is_empty():
        return false
    return bool(native_bridge.openDocument(uri))

func device_summary() -> String:
    if native_bridge != null:
        return str(native_bridge.deviceSummary())
    return "Platform: %s\nBridge: preview\nLocale: %s" % [OS.get_name(), TranslationServer.get_locale()]

func _normalize_url(url: String) -> String:
    var safe_url := url.strip_edges()
    if safe_url.is_empty():
        return ""
    if not safe_url.begins_with("http://") and not safe_url.begins_with("https://"):
        safe_url = "https://" + safe_url
    return safe_url

func _on_native_termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int) -> void:
    termux_result.emit(execution_id, stdout, stderr, exit_code)

func _on_native_directory_picked(uri: String) -> void:
    directory_picked.emit(uri)

func _on_native_download_started(file_name: String) -> void:
    download_started.emit(file_name)

func _on_native_bridge_error(message: String) -> void:
    bridge_error.emit(message)
