extends Node

signal changed

const SAVE_PATH := "user://savegame.json"

var money := 250
var hunger := 82.0
var energy := 90.0
var hygiene := 78.0
var health := 100.0
var day := 1
var minute_of_day := 8 * 60

var _clock_accumulator := 0.0
var _autosave_accumulator := 0.0

func _ready() -> void:
    load_game()
    changed.emit()

func _process(delta: float) -> void:
    _clock_accumulator += delta
    _autosave_accumulator += delta

    if _clock_accumulator >= 1.0:
        var ticks := int(_clock_accumulator)
        _clock_accumulator -= ticks
        advance_minutes(ticks * 2, false)

    if _autosave_accumulator >= 15.0:
        _autosave_accumulator = 0.0
        save_game()

func advance_minutes(minutes: int, save_after: bool = true) -> void:
    if minutes <= 0:
        return

    minute_of_day += minutes
    while minute_of_day >= 24 * 60:
        minute_of_day -= 24 * 60
        day += 1

    hunger = clamp(hunger - minutes * 0.012, 0.0, 100.0)
    energy = clamp(energy - minutes * 0.008, 0.0, 100.0)
    hygiene = clamp(hygiene - minutes * 0.006, 0.0, 100.0)

    if hunger <= 5.0 or energy <= 5.0:
        health = clamp(health - minutes * 0.015, 0.0, 100.0)

    changed.emit()
    if save_after:
        save_game()

func sleep_hours(hours: int) -> void:
    hours = clamp(hours, 1, 12)
    advance_minutes(hours * 60, false)
    energy = clamp(energy + hours * 11.0, 0.0, 100.0)
    hunger = clamp(hunger - hours * 1.8, 0.0, 100.0)
    changed.emit()
    save_game()

func eat(cost: int = 8, nutrition: float = 28.0) -> bool:
    if money < cost:
        return false
    money -= cost
    hunger = clamp(hunger + nutrition, 0.0, 100.0)
    advance_minutes(20, false)
    changed.emit()
    save_game()
    return true

func shower() -> void:
    hygiene = 100.0
    advance_minutes(15, false)
    changed.emit()
    save_game()

func work_shift() -> void:
    advance_minutes(4 * 60, false)
    money += 65
    energy = clamp(energy - 18.0, 0.0, 100.0)
    hunger = clamp(hunger - 12.0, 0.0, 100.0)
    changed.emit()
    save_game()

func time_text() -> String:
    var hour := int(minute_of_day / 60)
    var minute := minute_of_day % 60
    return "Day %d  %02d:%02d" % [day, hour, minute]

func save_game() -> bool:
    var file := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
    if file == null:
        return false

    var payload := {
        "version": 1,
        "money": money,
        "hunger": hunger,
        "energy": energy,
        "hygiene": hygiene,
        "health": health,
        "day": day,
        "minute_of_day": minute_of_day
    }
    file.store_string(JSON.stringify(payload))
    return true

func load_game() -> bool:
    if not FileAccess.file_exists(SAVE_PATH):
        return false

    var file := FileAccess.open(SAVE_PATH, FileAccess.READ)
    if file == null:
        return false

    var parsed = JSON.parse_string(file.get_as_text())
    if typeof(parsed) != TYPE_DICTIONARY:
        return false

    money = int(parsed.get("money", money))
    hunger = float(parsed.get("hunger", hunger))
    energy = float(parsed.get("energy", energy))
    hygiene = float(parsed.get("hygiene", hygiene))
    health = float(parsed.get("health", health))
    day = int(parsed.get("day", day))
    minute_of_day = int(parsed.get("minute_of_day", minute_of_day))
    return true
