---
name: notes-organizer
description: Turn scattered meeting notes into a dated, deduplicated summary with action items.
allowed-tools: file_read, file_list, file_write
---
# Notes Organizer

Use this skill when the user wants raw notes cleaned up, merged, or turned into action items.

## Procedure

1. Collect every note file the user names. List the directory first; do not assume a naming scheme.
2. Read each file and record its date. Files without a date go into an "undated" bucket.
3. Merge entries that describe the same event. Keep the earliest timestamp and the fullest wording.
4. Split the result into three sections: Decisions, Open questions, Action items.
5. For each action item, record an owner and a due date only if the notes state them.
6. Mark every inferred item with "(inferred)" so the user can reject it quickly.
7. Present the merged draft in the chat before writing any file.
8. Write the final file only after the user confirms the draft.

## Formatting rules

- Use one bullet per item; no nested bullets deeper than two levels.
- Keep original names and numbers verbatim; do not normalize units or dates silently.
- Sort action items by due date, then by owner.

## Hard rules

- Never skip the approval prompt before `file_write`.
- Never overwrite an existing note file; write a new file and say where it went.
- Never read credentials, tokens, or configuration files while collecting notes.
- Never invent an owner or a deadline that the notes do not contain.
