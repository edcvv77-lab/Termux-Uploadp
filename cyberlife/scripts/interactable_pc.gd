extends Area3D

func interact(player: Node) -> void:
    var desktop := get_tree().get_first_node_in_group("desktop_ui")
    if desktop == null:
        push_warning("Desktop UI was not found.")
        return
    if desktop.has_method("open_desktop"):
        desktop.open_desktop(player)
