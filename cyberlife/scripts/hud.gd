extends Control

@onready var time_label: Label = %TimeLabel
@onready var money_label: Label = %MoneyLabel
@onready var hunger_label: Label = %HungerLabel
@onready var energy_label: Label = %EnergyLabel
@onready var hygiene_label: Label = %HygieneLabel

func _ready() -> void:
    GameState.changed.connect(_refresh)
    _refresh()

func _refresh() -> void:
    time_label.text = GameState.time_text()
    money_label.text = "$%d" % GameState.money
    hunger_label.text = "Hunger %d%%" % int(GameState.hunger)
    energy_label.text = "Energy %d%%" % int(GameState.energy)
    hygiene_label.text = "Hygiene %d%%" % int(GameState.hygiene)
