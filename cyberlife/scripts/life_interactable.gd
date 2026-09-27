extends Area3D

@export_enum("sleep", "eat", "shower", "work") var action := "eat"

func interact(_player: Node) -> void:
    match action:
        "sleep":
            GameState.sleep_hours(8)
        "eat":
            GameState.eat()
        "shower":
            GameState.shower()
        "work":
            GameState.work_shift()
