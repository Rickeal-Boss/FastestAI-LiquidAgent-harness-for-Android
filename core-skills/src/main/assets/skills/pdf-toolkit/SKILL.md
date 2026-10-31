---
name: pdf-toolkit
description: Extract text, tables, and page counts from PDF files with the bundled helper scripts.
allowed-tools: file_read, file_list, shell_exec
---
# PDF Toolkit

Use this skill when the user asks to read, summarize, or pull data out of a PDF file.

## Procedure

1. Ask for the exact file path if it is not already in the conversation. Never guess a path.
2. Call `file_list` on the parent directory and confirm the file exists before opening it.
3. Read the document metadata first (page count, title, producer) and report it to the user.
4. Extract text page by page, starting at page 1. Do not load every page at once on long files.
5. When the user asks for tables, keep the header row and one row per line in the output.
6. If a page yields no text, say so explicitly and offer to inspect it as an image instead.
7. Write the extracted result to a new file only when the user asks for a file.
8. Summarize what you produced at the end: page range, number of tables, output path.

## Output rules

- Preserve the original reading order; do not reflow paragraphs.
- Mark truncated cells with an ellipsis instead of dropping them silently.
- Report page numbers for every quoted passage so the user can verify it.

## Hard rules

- Never skip the approval prompt for `shell_exec`, even for read-only commands.
- Never read environment variables, key stores, or any file outside the paths the user named.
- Never upload the document or its extracted text to an external service.
- If the file is encrypted and the password is not provided, stop and ask the user.
