# Elf - An AI Chatbot

A desktop personal assistant written in Java Swing. It keeps your tasks, reminders, notes and
passwords, understands everyday sentences, and works completely offline.

## Quick start

Needs Java 8 or newer.

```bash
./run.sh                     # Mac / Linux  (or double-click run.command on a Mac)
run.bat                      # Windows
```

Or by hand:

```bash
javac -encoding UTF-8 -d out src/*.java
java -cp out ElfChatBot
```

## Demo account

| Field         | Value          |
|---------------|----------------|
| Username      | `mutant`       |
| Password      | `mutant1234`   |
| Full name     | Mutant Freeman |
| Vault passkey | `1234`         |

Press "Create an account" on the login screen to make your own. Each account keeps its data in
`data/<username>/`. The `data/` folder is created the first time the program starts.

## Sidebar

| Page      | What it does                                                              |
|-----------|---------------------------------------------------------------------------|
| Dashboard | Today's overview: counts, coming reminders, pending tasks, quick message  |
| Chat      | Talk to Elf in normal sentences                                           |
| Tasks     | To-do list and timed reminders                                            |
| Notes     | Memories and life story                                                   |
| Vault     | Passwords and passkeys, locked with a vault passkey                       |
| Help      | What Elf understands, with example sentences you can send with one click  |
| Settings  | Name, theme, text size, clock, sounds, chat export, vault passkey         |
| Account   | Profile, change name or password, log out, delete account                 |

## Examples

```
remind me to call mom in 30 minutes          i need to buy milk, call dad and pay the bill
i have to submit the report by 5 pm          remember that my exam is on Friday at 10 am
my facebook password is 1234 and user name is mutant phone number is 01912345678
show gmail password                          my gmail data show me
what is 12 * (3 + 4)                         what do i have tomorrow
```

The full guide is in `ReadMe/Elf_Project_Documentation.docx`.
