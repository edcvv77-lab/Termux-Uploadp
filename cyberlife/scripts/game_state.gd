extends Node

signal changed

var money := 250
var hunger := 82.0
var energy := 90.0
var hygiene := 78.0
var health := 100.0
var day := 1
var minute_of_day := 8 * 60

var _clock_accumulator := 0.0

func _process(delta: float) -> void:
    _clock_accumulator += delta
    if _clock_accumulator < 1.0:
        return

    var ticks := int(_clock_accumulator)
    _clock_accumulator -= ticks
    advance_minutes(ticks * 2)

func advance_minutes(minutes: int) -> void:
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

func sleep_hours(hours: int) -> void:
    hours = clamp(hours, 1, 12)
    advance_minutes(hours * 60)
    energy = clamp(energy + hours * 11.0, 0.0, 100.0)
    hunger = clamp(hunger - hours * 1.8, 0.0, 100.0)
    changed.emit()

func eat(cost: int = 8, nutrition: float = 28.0) -> bool:
    if money < cost:
        return false
    money -= cost
    hunger = clamp(hunger + nutrition, 0.0, 100.0)
    advance_minutes(20)
    changed.emit()
    return true

func shower() -> void:
    hygiene = 100.0
    advance_minutes(15)
    changed.emit()

func work_shift() -> void:
    advance_minutes(4 * 60)
    money += 65
    energy = clamp(energy - 18.0, 0.0, 100.0)
    hunger = clamp(hunger - 12.0, 0.0, 100.0)
    changed.emit()

func time_text() -> String:
    var hour := int(minute_of_day / 60)
    var minute := minute_of_day % 60
    return "Day %d  %02d:%02d" % [day, hour, minute]
