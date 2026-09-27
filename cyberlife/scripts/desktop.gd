extends Control

@onready var terminal_panel: Control = %TerminalPanel
@onready var browser_panel: Control = %BrowserPanel
@onready var files_panel: Control = %FilesPanel
@onready var phone_panel: Control = %PhonePanel
@onready var terminal_output: RichTextLabel = %TerminalOutput
@onready var command_input: LineEdit = %CommandInput
@onready var url_input: LineEdit = %UrlInput
@onready var status_label: Label = %StatusLabel
@onready var phone_info: Label = %PhoneInfo
@onready var files_list: ItemList = %FilesList

var player_ref: Node = null
var file_entries: Array = []

func _ready() -> void:
    visible = false
    %CloseButton.pressed.connect(close_desktop)
    %TerminalButton.pressed.connect(func(): _show_panel(terminal_panel))
    %BrowserButton.pressed.connect(func(): _show_panel(browser_panel))
    %FilesButton.pressed.connect(func(): _show_panel(files_panel); _refresh_files())
    %PhoneButton.pressed.connect(func(): _show_panel(phone_panel))
    %RunButton.pressed.connect(_run_command)
    %PermissionButton.pressed.connect(_request_termux_permission)
    command_input.text_submitted.connect(func(_text): _run_command())
    %OpenUrlButton.pressed.connect(_open_in_app_browser)
    %ExternalBrowserButton.pressed.connect(_open_external_url)
    %PickFolderButton.pressed.connect(_pick_folder)
    %RefreshFilesButton.pressed.connect(_refresh_files)
    %OpenFileButton.pressed.connect(_open_selected_file)
    AndroidBridge.termux_result.connect(_on_termux_result)
    AndroidBridge.directory_picked.connect(_on_directory_picked)
    AndroidBridge.download_started.connect(_on_download_started)
    AndroidBridge.bridge_error.connect(_on_bridge_error)

func open_desktop(player: Node) -> void:
    player_ref = player
    visible = true
    if player_ref != null and player_ref.has_method("set_gameplay_controls"):
        player_ref.set_gameplay_controls(false)
    status_label.text = "ANDROID BRIDGE" if AndroidBridge.has_native_bridge() else "PREVIEW"
    phone_info.text = AndroidBridge.device_summary()
    %TermuxState.text = "Termux detected" if AndroidBridge.is_termux_available() else "Termux unavailable / permission not configured"
    _refresh_files()
    command_input.grab_focus()

func close_desktop() -> void:
    visible = false
    GameState.save_game()
    if player_ref != null and player_ref.has_method("set_gameplay_controls"):
        player_ref.set_gameplay_controls(true)
    player_ref = null

func _show_panel(panel: Control) -> void:
    for p in [terminal_panel, browser_panel, files_panel, phone_panel]:
        p.visible = p == panel

func _run_command() -> void:
    var command := command_input.text.strip_edges()
    if command.is_empty():
        return

    terminal_output.append_text("\n[color=#7ee787]$ %s[/color]\n" % command)

    match command:
        "clear":
            terminal_output.clear()
        "help":
            terminal_output.append_text("Local: help, clear, status\nAll other commands are sent exactly as typed to your Termux environment.\n")
        "status":
            terminal_output.append_text(AndroidBridge.device_summary() + "\n")
        _:
            var result := AndroidBridge.run_termux_command(command)
            terminal_output.append_text(str(result.message) + (" #%s\n" % result.execution_id if result.has("execution_id") else "\n"))

    command_input.clear()

func _request_termux_permission() -> void:
    if AndroidBridge.request_termux_permission():
        %TermuxState.text = "Permission requested. allow-external-apps=true must also be enabled in Termux."
    else:
        %TermuxState.text = "Android bridge not available."

func _open_in_app_browser() -> void:
    var result := AndroidBridge.open_in_app_browser(url_input.text)
    %BrowserMessage.text = str(result.message)

func _open_external_url() -> void:
    var result := AndroidBridge.open_url(url_input.text)
    %BrowserMessage.text = str(result.message)

func _pick_folder() -> void:
    var result := AndroidBridge.pick_directory()
    %FilesMessage.text = str(result.message)

func _refresh_files() -> void:
    files_list.clear()
    file_entries = AndroidBridge.list_selected_directory()
    var root_uri := AndroidBridge.selected_directory_uri()

    if root_uri.is_empty():
        %FilesMessage.text = "No phone folder selected yet."
        return

    if file_entries.is_empty():
        %FilesMessage.text = "Selected folder is empty or cannot be read."
        return

    for entry in file_entries:
        var is_dir := bool(entry.get("is_directory", false))
        var name := str(entry.get("name", "Unnamed"))
        var size := int(entry.get("size", -1))
        var prefix := "📁 " if is_dir else "📄 "
        var suffix := ""
        if not is_dir and size >= 0:
            suffix = "  (%s)" % _format_bytes(size)
        files_list.add_item(prefix + name + suffix)

    %FilesMessage.text = "%d real items from the selected Android folder." % file_entries.size()

func _open_selected_file() -> void:
    var selected := files_list.get_selected_items()
    if selected.is_empty():
        %FilesMessage.text = "Select a file first."
        return

    var index := int(selected[0])
    if index < 0 or index >= file_entries.size():
        return

    var entry: Dictionary = file_entries[index]
    if bool(entry.get("is_directory", false)):
        %FilesMessage.text = "Folder navigation will be added next. Select a file to open it now."
        return

    var uri := str(entry.get("uri", ""))
    if AndroidBridge.open_document(uri):
        %FilesMessage.text = "Opened: " + str(entry.get("name", "file"))
    else:
        %FilesMessage.text = "Android could not open this file."

func _format_bytes(value: int) -> String:
    var amount := float(value)
    var units := ["B", "KB", "MB", "GB"]
    var unit_index := 0
    while amount >= 1024.0 and unit_index < units.size() - 1:
        amount /= 1024.0
        unit_index += 1
    return "%.1f %s" % [amount, units[unit_index]]

func _on_termux_result(execution_id: int, stdout: String, stderr: String, exit_code: int) -> void:
    terminal_output.append_text("\n[color=#8bd5ca]#%d exit=%d[/color]\n" % [execution_id, exit_code])
    if not stdout.is_empty():
        terminal_output.append_text(stdout + ("\n" if not stdout.ends_with("\n") else ""))
    if not stderr.is_empty():
        terminal_output.append_text("[color=#f38ba8]" + stderr + "[/color]\n")

func _on_directory_picked(uri: String) -> void:
    %FilesMessage.text = "Folder access saved."
    _refresh_files()

func _on_download_started(file_name: String) -> void:
    %BrowserMessage.text = "Downloading to Android Downloads: " + file_name

func _on_bridge_error(message: String) -> void:
    terminal_output.append_text("\n[color=#f38ba8]Bridge: %s[/color]\n" % message)
