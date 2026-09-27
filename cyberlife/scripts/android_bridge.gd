extends Node

var native_bridge: Object = null

func _ready() -> void:
    if Engine.has_singleton("CyberLifeBridge"):
        native_bridge = Engine.get_singleton("CyberLifeBridge")

func has_native_bridge() -> bool:
    return native_bridge != null

func run_termux_command(command: String) -> Dictionary:
    command = command.strip_edges()
    if command.is_empty():
        return {"ok": false, "message": "Empty command."}

    if native_bridge != null and native_bridge.has_method("runTermuxCommand"):
        var result = native_bridge.call("runTermuxCommand", command)
        return {"ok": true, "message": str(result)}

    return {
        "ok": false,
        "message": "[Mock mode] Native Termux bridge is not installed yet. Command was not executed: " + command
    }

func open_url(url: String) -> Dictionary:
    var safe_url := url.strip_edges()
    if safe_url.is_empty():
        return {"ok": false, "message": "Enter a URL first."}
    if not safe_url.begins_with("http://") and not safe_url.begins_with("https://"):
        safe_url = "https://" + safe_url

    if native_bridge != null and native_bridge.has_method("openBrowser"):
        native_bridge.call("openBrowser", safe_url)
        return {"ok": true, "message": "Opened with Android bridge."}

    var err := OS.shell_open(safe_url)
    return {
        "ok": err == OK,
        "message": "Opened in the system browser." if err == OK else "Could not open URL."
    }

func pick_directory() -> Dictionary:
    if native_bridge != null and native_bridge.has_method("pickDirectory"):
        native_bridge.call("pickDirectory")
        return {"ok": true, "message": "Android folder picker opened."}

    return {
        "ok": false,
        "message": "[Mock mode] Android Storage Access Framework bridge is not installed yet."
    }

func device_summary() -> String:
    var mode := "native bridge" if has_native_bridge() else "mock bridge"
    return "Platform: %s\nBridge: %s\nLocale: %s" % [OS.get_name(), mode, TranslationServer.get_locale()]
