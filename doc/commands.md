# Management commands
Requires OP level 2

Command | Description
--- | ---
`/parkour_admin create start` | Start creating a new parkour course, the starting pressure plate will be at your current location
`/parkour_admin create checkpoint` | Places a checkpoint at your current location if you are working on a parkour
`/parkour_admin create end <id: identifier> <display name: JSON text component>` | Finishes creating a new parkour and sets its end position to your current location
`/parkour_admin delete <parkour> [confirm]` | Deletes a parkour course
`/parkour_admin rename <parkour> <name>` | Changes the display name of a parkour course.

## Example parkour creation workflow
1. Build the parkour in-game
2. Go to the place you want the start plate for the parkour and run `/parkour_admin create start`
3. Go to the locations of each checkpoint in order and run `/parkour_admin create checkpoint`
4. Go to the end of the parkour and run `/parkour_admin create end <id> <display name>`
5. Place pressure plates (any type) at the start, end and at any checkpoints.
6. Profit

# Player commands
Any player can use

Command | Description
--- | ---
`/parkour time <parkour>` | Shows your time for a parkour
`/parkour cancel` | Stops your current parkour run
