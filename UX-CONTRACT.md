# LocalMind UX Contract

## Canonical Owners

| Capability | Owner | Behavior |
| --- | --- | --- |
| Chat command | `MainActivity` composer | One submit at a time; deterministic actions route before model generation |
| Confirmation | Android `AlertDialog` | Names the action and consequence; Cancel is always available |
| Local data navigation | Header overflow menu | Opens the `My data` summary without replacing chat |
| Memory list | Native single-choice dialog | Select an item to edit or delete; empty state explains explicit save command |
| Reminder list | Native single-choice dialog | Select a pending item to delete; empty state explains creation command |
| Date/time parsing | `BasicDeviceActionParser` | English `today`, `tomorrow`, or ISO date plus explicit time; ambiguous input does not schedule |
| Feedback | Assistant message plus private activity event | Success is reported only after persistence/scheduling returns successfully |

## Data Lifecycle

- Memories are saved only after an explicit `remember` command.
- Memory search reads only the local Room database.
- A memory can be edited or deleted from My data; clearing all requires confirmation.
- Reminder creation requires a visible confirmation and notification permission where Android requires it.
- Reminder deletion cancels its pending alarm and deletes its database row.
- Reboot restoration schedules only enabled future reminders.
- Clearing app storage removes all LocalMind data, including models.

## Failure And Recovery

- Database or scheduling failures leave the composer usable and report that the action did not complete.
- Notification denial creates no reminder because the user would receive no visible result.
- Past or ambiguous reminder times are not guessed.
- Repeated taps are blocked while local storage work is active.

## Accessibility

- Native controls retain Android semantics and focus behavior.
- Icon-only controls have content descriptions.
- Text remains readable at system font scaling; dialog content may scroll.
- Status messages use text, not color alone.
