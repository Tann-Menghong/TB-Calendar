# Changelog

## 1.10.0 — 2026-09-09

Countdowns you choose yourself, free time where you need it, and a task urgency that stopped disappearing.

### រាប់ថយក្រោយ · Count down to what matters to you

The countdown card used to pick for you: the next public holiday, and the next event in your calendar. That is a reasonable default and not a countdown feature — the whole point is that *you* decide what is worth counting, and there was no way to say so.

**Open any event and tap the pin.** That date is now a countdown. Exams, deadlines, birthdays, a trip, a project — anything already in your calendar, which is where those dates live anyway.

**A pinned countdown is an event, not a second kind of thing.** It inherits everything an event already has. That is not tidiness for its own sake — it is what makes a birthday countdown work at all: pin a yearly repeat and the countdown reads its *next* occurrence, not the date years ago when you first entered it. A birthday pinned from June 2024 counts to June 2027 once this June has gone, without anyone maintaining it. Search, categories, colours, reminders, backup and ICS export come along for free too.

**Three ways of writing the remaining time**, chosen on the countdown screen itself where you can see them change: **ថ្ងៃ** (នៅ ៥១ ថ្ងៃ), **ថ្ងៃ និងម៉ោង** (នៅ ៥០ ថ្ងៃ ២១ ម៉ោង), **សប្តាហ៍** (នៅ ៧ សប្តាហ៍ ២ ថ្ងៃ). Three, because they answer three different questions and everything past that is decoration. An all-day entry is never given an hours figure — claiming "៣ ថ្ងៃ ៧ ម៉ោង" until a birthday is a precision the date does not have.

Today and tomorrow are always named rather than counted. A countdown reading "នៅ ១ ថ្ងៃ" makes you do the arithmetic it was supposed to do for you.

**A countdown that has arrived is kept, not deleted.** It moves to បានកន្លងផុត with its pin still on. The exam you sat is your data; the app does not tidy it away on your behalf.

The **រាប់ថយក្រោយ** button in the dashboard's quick row now opens this screen. It used to open the holiday list, which is not what the button said.

### ទំនេរ · When you are free, on the day itself

The day view now shows your free stretches above the timetable — the first two, and a count of the rest. On today it will not offer you a morning that has already gone: at 9:38 the answer starts at 9:38.

The assistant could already work this out when asked. That was the wrong place for it in two ways: you had to think to ask, and the assistant is optional and off by default, so for most people the answer did not exist. The arithmetic has moved down into the calendar engine beside the lunar and recurrence code, where it belongs — it is interval subtraction, nothing to do with a model — and the assistant now calls the same function.

Moving it earned twelve new tests for cases the assistant's own tests never reached: an unsorted list of meetings, a short meeting nested inside a long one (which under the obvious implementation invents a free gap that is already booked), and something that started before your day did.

### The task urgency that quietly reset

Setting a task to **សំខាន់**, then opening it in the full editor and saving, put it back to **ធម្មតា**.

The editor loaded every field of the event except its urgency, so the draft it held always said "ធម្មតា" — and saving wrote that draft back over the real value. Nothing failed; the setting simply went, and only if you happened to look afterwards would you know.

This is the fourth time this codebase has produced the same bug: a value written to the database that some path forgets to read. It is invisible to the compiler, to lint, and to every test that does not do the round trip.

### The database changed, and that was tested before it shipped

The pin needed a column, so the schema moved to version 3. Same treatment as last time: no destructive fallback is configured, so a bad migration makes the app fail to open rather than quietly deleting your calendar — which is the safer failure and still one nobody should ship.

The migration test now covers **both** paths: version 1 straight through to 3, which is the upgrade anybody still on an early version will actually run, and version 2 to 3 on its own — because a 1→3 test passes through 1→2 first and could hide a broken 2→3 behind it. It was then run the way you will run it, installing over a real version-2 database on an Android 8.0 device: all twelve events survived and the new column read back as unpinned.

### Tested

**302 unit tests** pass and lint is clean, including 32 new ones covering countdown selection and phrasing in all three styles, free-time subtraction, and the schema migration.

Checked on an **Android 8.0 (API 26)** emulator: the upgrade over a real version-2 database, pinning and unpinning from both the event and the countdown screen, a yearly birthday resolving forward to its next occurrence, all three styles, the free-time row trimming the morning that had gone, and both the urgency and the pin surviving a save from the full editor.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

### Install

Android 8.0 (API 26) or later. Download `TB-Calendar-1.10.0.apk` below. Upgrading keeps your events, tasks, notes and settings.

## 1.9.0 — 2026-09-08

The calendar had four views. Three of them were nearly impossible to find.

### ខែ · សប្តាហ៍ · ថ្ងៃ · បញ្ជី

The month, the week, the day and the agenda are now one screen with a switcher across the top.

They have all existed since early versions and all four were written, tested and shipped — but only the month was a tab. The day opened if you tapped a date. The week view could be reached in exactly one way: by going into settings and making it the screen the app *starts* on. The agenda was behind a small link on two other screens. For most people the app had a month grid and nothing else, which is not what it was.

**One header drives all four.** Where you are, one step back, one step forward, and a way back to today. "Next" means the next month in the month view, the next week in the week view and tomorrow in the day view, because the header steps in whatever unit is on screen rather than in whichever unit somebody wrote first.

**ថ្ងៃនេះ appears only when today is off screen** — and it asks that of the whole visible span, not of the selected date. In the month view you can be looking at September with the 3rd selected while today is the 20th; offering to jump somewhere that would not move the grid is worse than offering nothing.

**Jump to a date.** The calendar icon in the header opens a date picker and takes the whole calendar there. Getting to March 2028 no longer means swiping eighteen times.

**The agenda has no arrows**, because it is a rolling list that starts at today rather than a window onto a date. It gets the search button instead. A control that is shown but does nothing teaches people not to trust the other controls.

**A day in the week view opens that day.** The week is a way into the day now rather than a dead end.

### The month grid gives way to the day beneath it

The panel under the month grid — the events of whatever day you just tapped — was being squeezed to nothing on an ordinary phone. Six rows at the comfortable density plus the weekday captions plus the app's own chrome is more than a 640dp screen has, and the panel is the last thing in the column, so it got whatever was left: on the test device, zero pixels. Tapping a date appeared to do nothing at all.

The density setting is a preference, not a promise. The cells now shrink until the panel has room for a day's headline and an event, and stop at a size that is still a comfortable target.

### Double-booking, marked where it happens

The assistant could already answer "find my conflicts" when asked. That is the wrong moment to learn about a clash — it matters on the day it falls on, not when you think to ask. Overlapping events are now marked in the day view: a count at the top, and a mark on each event involved.

An icon and a word, never colour alone, because the row is already tinted with the event's own colour, which you chose and which says nothing about clashes.

All-day entries are not counted — an all-day event covers everything else on the day, so flagging it would mark the whole screen. Nor are completed items, or tasks: a to-do due at three o'clock is not a booking at three o'clock, and treating it as one would flag every deadline that landed during a meeting.

### The start-screen setting never worked on a cold start

Choosing "ប្រតិទិនសប្តាហ៍" as the screen the app opens on did nothing. The navigation host reads its start destination exactly once, on the first frame; the stored settings arrive from disk a moment after that, so the first frame always saw the defaults. The theme flashed the default accent for the same reason.

The first frame now waits for the settings, behind the splash screen that was already there. A failed read reports "loaded" and the app opens on defaults, so this cannot strand anybody on a splash screen.

Found by reading the code while wiring the four views together, then reproduced on the device: set the week view as the start screen, force-stop, reopen, and you got the dashboard.

### Tested

**270 unit tests** pass and lint is clean, including 18 new ones covering the header's stepping in each view, the week span under both week starts, and what does and does not count as a conflict.

Checked on an **Android 8.0 (API 26)** emulator: all four views and the switcher, stepping a week forward and the "today" button appearing, jump-to-date across a month boundary, a real double-booking marked in the day view, a day opened from the week view, the widget's day link, and the start-screen setting surviving a cold start.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

### Install

Android 8.0 (API 26) or later. Download `TB-Calendar-1.9.0.apk` below. Upgrading keeps your events, tasks, notes and settings; the database is unchanged from 1.8.0.

## 1.8.0 — 2026-09-08

A to-do list that lives inside the calendar, a month you can read at a glance, and a dashboard that finally lets you name it.

### កិច្ចការ · A to-do list, connected to the calendar

The **កិច្ចការ** tab is now a real to-do list instead of a filtered agenda.

**It is a view of your calendar, not a list beside it.** Every line here is an entry in the same table the month grid and the day timeline read. A task added at the top of this screen appears on its day everywhere else in the app a moment later; ticking one off changes the dashboard's progress ring. The alternative — a separate tasks table — is easier to write and worse to live with: two stores that have to agree about what a day contains, and a "sync" you only notice when it stops working.

**The sections are relative, not dates.** **ហួសកំណត់** / **ថ្ងៃនេះ** / **ថ្ងៃស្អែក** / **សប្តាហ៍នេះ** / **ក្រោយៗ**. A to-do list answers *what do I have to do now*, and a date only answers that once you have worked out what today is. Overdue leads, because work you have already missed is the most useful thing the screen can tell you and the easiest thing for a forward-looking list to hide. "សប្តាហ៍នេះ" ends where *your* week ends — the week-start preference, not a Sunday somebody assumed.

**ថ្ងៃនេះ is always there**, even empty. A list whose first heading is "ក្រោយៗ" reads as though today were already dealt with.

**One-line add.** A title and a day — ថ្ងៃនេះ, ស្អែក, សប្តាហ៍ក្រោយ — and nothing else. The day and the urgency stay put after each add, so five things for Thursday means picking Thursday once. Anything needing a clock, a reminder, a category or a repeat is in the full editor, one tap away under **លម្អិត**.

**Three levels of urgency: ធម្មតា, សំខាន់, ទាប.** Tap the dot on a row to cycle it. Three, because the difference between "high" and "normal" is a decision anyone can make in a second and the difference between a four and a three is not.

**FOCUS on the dashboard is now ranked** — overdue first, then urgency — instead of showing whichever three tasks the query happened to return first.

#### The database changed, and that was tested before it shipped

Task urgency needed a new column, so the schema moved to version 2. This app has no destructive fallback configured, which is the right choice for somebody's calendar: a bad migration makes the app fail to open rather than quietly deleting everything. That is still a failure nobody should ship, so the migration is covered by a test that builds a database at the *old* version — from the exported v1 schema, not a transcription of it — writes events, reminders, categories and notes into it, migrates, and checks every field survived.

It was then run again the way you will run it: installing over a real version-1 database on an Android 8.0 device. All eleven events survived, and the new column read back as ធម្មតា.

### ទិដ្ឋភាពខែ · The month, at a glance

A new dashboard module. The week module answers "is Thursday going to be bad"; this answers the question one step out — where the heavy stretches of the month are, and how much of it is still free. Seven columns and five rows fit in the height of two ordinary cards, which is why a heatmap earns a place on a dashboard where a month grid would not.

Shade carries the load and the number carries the date, so a cell is still readable when the shade is not. Tap any day to open it.

### More of the dashboard is yours

**ប៊ូតុងរហ័ស.** The four buttons at the foot of the dashboard were chosen by whoever wrote them. Now you choose them, from eight. Turning on a fifth replaces the earliest rather than refusing — a switch that silently will not move is worse than one that explains itself, and the row tells you in advance which one is going.

**ឈ្មោះរបស់អ្នក.** An optional name in the greeting. It never leaves the device, and it does not create an account — the app still has none.

**Modules you switched on always draw something.** They used to vanish when empty, which reads as the app losing them, and became actively confusing once the editor started saying "showing 12 of 12" beside a dashboard visibly showing nine. The exception is a module whose data you turned off elsewhere, like the lunar date: that one is disabled, not empty, and drawing a shell for it would be arguing with a setting you already changed.

### The dashboard's clock was stopped

Every module was calling for the current time *during composition*, which is not a state: the value was captured when the dashboard first drew and never changed again. The "starting soon" badge and the timeline's now-marker were both frozen at whatever time the screen happened to open, and the "next event" card kept pointing at a meeting that had already finished. There is now one ticking clock for the whole dashboard, at the resolution any of them actually shows.

### Two more things the device found

**Typing a name lost letters.** The field wrote the preference on every keystroke; each write came back through the settings flow and reset the field to whatever had been saved by then. "Sophea" became "Sohe". The write is now debounced, which also stops a name costing one disk commit per letter.

**Today's ring in the month grid was invisible** on a busy day — the ring was drawn in the accent colour, and on a busy day the cell *is* the accent colour.

Neither of these is visible in the code. Both took looking at the screen.

### Tested

**252 unit tests** pass and lint is clean, including 45 new ones covering the to-do sections and ordering, the month grid's column alignment under both week starts, the dock's four-button limit, and the schema migration.

Checked on an **Android 8.0 (API 26)** emulator: the upgrade over a real version-1 database, adding a task and watching it appear in the week bars and the month grid, the ranked focus list, the dock replacing a button, and the greeting.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

### Install

Android 8.0 (API 26) or later. Download `TB-Calendar-1.8.0.apk` below. Upgrading keeps your events, tasks, notes and settings; the new month module joins the end of your dashboard layout rather than rearranging it.

## 1.7.0 — 2026-09-08

1.6.0 made the dashboard move. This one makes it **yours** — and adds the view it was missing.

### ទិដ្ឋភាពសប្តាហ៍ · The week, as seven bars

The dashboard could tell you about today, and about the next sixty days, and had nothing to say about the week you are actually in — which is the horizon most people plan against. Seven bars answer *is Thursday going to be bad* in one glance, which no amount of scrolling a list does.

**Events and tasks stack in different tones** rather than adding up into one bar. Eight events is a full day; eight tasks is a full week. One bar would say they were the same thing.

Height is never the only signal: every bar keeps its date under it, today is a filled pill rather than merely a taller bar, and a free day draws a baseline stub so the week reads as seven days one of which is empty — not as six days.

A week with a single event does not draw one full-height bar and six empty ones. A bar chart's job is to compare, and there is nothing to compare one value against, so the scale has a floor.

Tap any bar to open that day.

#### The query had to move first

The dashboard loaded events from today onwards, so Monday and Tuesday were simply not in memory by Thursday and the first bars would have been empty. The window now starts at the first day of *your* week — the week-start preference, not Monday — and everything that means "what is coming up" is clamped back to today, so no existing figure on the dashboard changed.

### រៀបចំផ្ទាំងដើម · The dashboard editor

Which modules appear, and in what order, was a block at the bottom of the appearance screen, below the wallpaper opacity slider. The one control that decides what the app's main screen contains was the last thing on a long page. It is now its own screen — **ការកំណត់ → ផ្ទាំងដើម** — with three ways to arrange it, on purpose:

**គំរូ · Presets.** One tap: **សាមញ្ញ**, **ផ្តោតលើការងារ**, **រៀបចំផែនការ**, **ទាំងអស់**. Each is a starting point, not a mode — everything stays editable afterwards. A preset *hides* what it does not name rather than dropping it, so no module ever becomes unrecoverable, and the row tells you honestly when your layout is your own rather than showing a preset that no longer matches your screen.

**Dragging**, by the grip on the left, for rearranging several modules at once.

**The arrows**, which are not a fallback. They are reachable one-handed, they work with a screen reader, and they need no gesture you have to discover first. Drag was added *alongside* them for exactly that reason. The drag deliberately does not auto-scroll: a list being reordered under a finger while it scrolls itself is where this kind of control usually starts losing items, so a long move is still an arrows job.

Applying a preset or resetting writes the order and the hidden set in one operation. Half of an arrangement would leave a module ordered into a place it is also hidden from.

### ចុចឱ្យយូរលើផ្ទាំងណាមួយ · Long-press any module

Hide it, move it up, move it down, or open the editor — without leaving the dashboard, with the module you are changing under your finger. A long press is invisible to a screen reader, so the same menu is offered there as a named action on each module.

Hiding says where to get it back from, because it is reversible and should not need a confirmation dialog.

### The header

**A greeting instead of the app's own name.** Someone who has just tapped the ប្រតិទិនខ្មែរ icon does not need telling which app opened. Four bands, not the usual three, because Khmer distinguishes រសៀល from ល្ងាច.

**It sheds two lines as you scroll** — the date, which the card below repeats, and the offline badge, which is a statement rather than a live value. The greeting, the clock and the two controls never move: the time and the way to search are wanted at any depth of the dashboard.

### ព្រឹត្តិការណ៍ខាងមុខ snaps

The up-next rail now settles on a card instead of stopping wherever the flick ran out. The cards are one width and read one at a time; a flick that halts between two of them leaves you looking at two halves.

### Fixed on the device

The long-press menu opened at half height, which was shorter than its own four actions — the last one sat under the navigation bar on an **Android 8.0** phone with three buttons, where it could not be tapped. Found by tapping it on that phone, not by reading the code.

A free day's baseline stub was drawn too faint to see, which made an empty week look like a broken chart.

### Tested

**207 unit tests** pass and lint is clean, including 27 new ones covering the week's arithmetic, the reorder rules and the greeting bands. Checked on an **Android 8.0 (API 26)** emulator with a seeded uneven week: the bars against the actual data, the preset applying and the dashboard changing to match, the reset restoring it, the arrows, the compact header, and the long-press menu clearing the navigation bar.

One thing was not: the drag-to-reorder gesture itself. `adb` on API 26 cannot express hold-then-drag, so it was not exercised on the device — the arrows do the same job and were.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

### Install

Android 8.0 (API 26) or later. Download `TB-Calendar-1.7.0.apk` below. Upgrading keeps your events, tasks, notes and settings — including your dashboard layout, which gains the week module at the end of it.

## 1.6.0 — 2026-09-08

1.5.0 built the dashboard's structure. This one makes it move — and puts every animation in the app behind one switch you control.

### ចលនា · You decide how much the interface moves

**ការកំណត់ → រូបរាង → ចលនា** — **តិចបំផុត**, **ធម្មតា** or **ច្រើន**.

**តិចបំផុត** does not mean "the same animations, faster". It collapses every duration to zero, so a state still changes — it simply arrives instead of travelling. That is what reduced motion should do: it is about movement, never about hiding information.

Your phone's own accessibility setting still wins over this one, and the app says so under the control.

#### Why this is one switch and not a convention

Two things can ask for less movement: Android's accessibility switch and the setting above. A convention that each animation "should check" would be honoured wherever somebody remembered and quietly broken everywhere else — and that failure is invisible in review, because the animation just runs for a user who asked it not to.

So durations now come from one place or they do not exist. Every animation in the app is gated by it, including the loading shimmer, the progress ring, the progress bars, the timeline and press feedback.

### What moves now

**The work card crossfades between states.** Morning work becoming lunch is a transition you see happen, rather than a card that was a different colour the next time you looked.

**The timeline arrives as a sequence** — each row fading and lifting a few pixels, staggered downward so the day assembles rather than appearing. The stagger is capped: a long day would otherwise take a second and a half to finish, which is an animation you wait on rather than enjoy. It runs once, and a minute passing does not replay it.

**Figures count to their new value.** Tick a task and the percentage counts up rather than jumping. Only on a real change — a number that animates every time it is recomposed is noise.

**Cards dip under the finger.** Three per cent, not ten: the point is confirming the touch landed on *this* card, and anything more reads as a toy.

### ចាប់ផ្តើមឆាប់ៗ · The next event tells you when it is close

Inside a quarter of an hour, the countdown stops being information and becomes a prompt, so **NEXT** says so — in words. Not by turning a colour, which would tell a colour-blind user nothing at all.

### A time inside the timeline read the wrong way

The break row said **រហូតដល់ 13:30** — Latin digits, 24-hour — inside a timeline where every other time read ១:៣០ រសៀល. The cause is worth naming: the timeline builder is pure and has no access to your 12/24-hour preference or your choice of numerals, and it was formatting a clock time anyway. It now carries the time and lets the screen render it. Covered by a test.

### Tested

181 unit tests pass and lint is clean. Checked on an **Android 8.0 (API 26)** emulator with a seeded working day: the "starting soon" badge at fourteen minutes out, the corrected break subtitle, and — the check that matters most — the whole dashboard at **តិចបំផុត**, where every row of the timeline is present and complete. Reduced motion must never hide information.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

## 1.5.0 — 2026-09-08

The Home tab is a different screen. Not a restyle of the old one — a different composition, a different reading order, and a piece of information it never showed before.

### The toolbar is gone

The dashboard used to open with a Material top app bar: a fixed strip of screen saying **ប្រតិទិនខ្មែរ** to somebody who had just tapped the ប្រតិទិនខ្មែរ icon. On the one screen whose whole job is to answer *what is happening now*, that was the least useful row available.

In its place is a status strip in the same height: the date, a clock running to the second, and the app's standing claim about itself — **OFFLINE · ទិន្នន័យនៅក្នុងឧបករណ៍**. That badge is static on purpose. It is not reporting a connection that might change; it is stating the design.

### TODAY, set as a headline

The old today card was tidy — greeting, clock, date, lunar line, summary, all at roughly one weight. Tidy was the problem: nothing in it was the answer, so you had to read all of it.

Now the day of the month is set at display size with the month and weekday beside it, the lunar date under a rule, and everything else as supporting text. You can read the date from across a room, which is what a calendar's front page is for.

### កាលវិភាគថ្ងៃនេះ · The day as a line, not a list

This is the change that matters most.

A list of today's events tells you *what is on*. A timeline tells you **where you are in it** — and that is the question the dashboard exists to answer. The day runs from clocking on to going home as one continuous rule, with your events threaded into it in clock order, everything already past dimmed, and a green marker at the current minute.

Your working day and your calendar are on the same line for the first time. An 11:00 meeting means something different when lunch starts at 11:30, and now you can see that without doing the arithmetic.

Twelve tests cover the awkward shapes: a day off, Saturday's single shift, an event landing exactly on a shift change, an all-day entry with no clock time to sit at.

### NEXT and TODAY, side by side

Two small modules in one row rather than another full-width card: what is coming, and how far through the day you are. A dashboard that is only ever one column deep reads as a list.

### FOCUS · Only the few things that need doing

Numbered, because a numbered list is a ranking and a bulleted one is a pile. Three at most, each with a tick to close it, and the full list one tap away. A dashboard that lists everything is a task manager.

### UP NEXT · a horizontal strip

What is coming after today — events and holidays as peers competing for a glance, scrolling sideways rather than pushing the rest of the dashboard off the screen.

### AI, made small

The assistant is a tool you reach for, not a thing you check, so it no longer takes a full card. It keeps the cyan-to-magenta sweep that marks it out as not-calendar-content, two verbs, and no more room than that.

### The floating button is gone

It had been covering whatever card happened to sit under it — a value one release, a quick action the next. In its place, a dock at the foot of the dashboard: **ព្រឹត្តិការណ៍ · កិច្ចការ · កំណត់ចំណាំ · រាប់ថយក្រោយ**. It blocks nothing, holds four actions instead of one, and reads as part of the dashboard rather than something dropped on top of it.

### Navigation named for subjects

**ដើម · ប្រតិទិន · កិច្ចការ · AI · ផ្សេងៗ** — named for what they hold rather than the view they open with. The second tab is the calendar, not "the month".

### Your layout is still yours

Every module can be reordered or hidden in **ការកំណត់ → រូបរាង → ផ្ទាំងដើម**, and the new ones are appended to a saved order rather than replacing it — so an arrangement you had made still stands, with the new modules added at the end.

### Tested

181 unit tests pass, up from 169, and lint is clean. Checked on an **Android 8.0 (API 26)** emulator with a seeded working day: the running clock, the timeline with its now-marker sitting between a 09:00 meeting and the 11:30 break, the two-up row, the focus list, the AI strip, the dock, and the customize list showing every new module. Verified in dark and in light, on the minified release build.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

## 1.4.1 — 2026-09-08

A follow-through on the 1.4.0 redesign: the card system now actually runs the dashboard.

### Every card, one system

1.4.0 built the card system and used it on three surfaces. The other nine cards on the dashboard were still hand-rolled — which meant the app had *two* card implementations, exactly the thing the system existed to end.

All nine go through one wrapper now, which owns the padding, the heading and the rule beside it. The old `SectionCard` is deleted rather than left lying around as a second way to do the same job.

### ពណ៌តាមប្រភេទ · Cards say what kind of thing they are about

Cards on a dashboard share a shape and a weight, so the eye needs another way to tell a work card from a holiday card while scanning. Calendar is cyan, work and tasks green, the assistant magenta, holidays gold.

Most of those follow whichever accent you picked, through the same derived trio as everything else. **Holidays deliberately do not.** A Cambodian public holiday is a cultural thing rather than a system state, and gold is what it is — making Pchum Ben neon-whatever would be the moment the futuristic styling started overwriting the culture it is meant to serve.

Colour is never the only signal. Every card still carries a heading in words, which is what a screen reader reads and what works for anyone who does not separate those hues.

### កិច្ចការ · Tasks show progress

A count and a bar, saying the same thing two ways on purpose: a bar alone is a shape you have to estimate, and a fraction alone is a number you have to picture.

### Holidays carry a countdown

**នៅ ១៦ ថ្ងៃ** beside each one, which is the thing you actually want from that row. The countdown card uses the same gold now — it was using the error colour, so the two cards described the same Pchum Ben in red on one and gold on the other, and red on a holiday reads as a warning.

### ទំហំកាត · Card density is yours

**ការកំណត់ → រូបរាង → ផ្ទាំងដើម → ទំហំកាត** — តូច, មធ្យម or ធំ. It changes the padding inside a card and the gap between cards, so five cards fit without scrolling if that is what you want, and nobody is forced into it if it feels cramped.

### Reduced motion is honoured

The progress ring and both progress bars check the system animator scale. One switch in accessibility settings stops all of it; nothing else changes.

### Also fixed

A flaky test added in 1.3.2 — a `runBlocking` inside a `runTest` scope parked the test dispatcher and leaked into the *next* test in the class, so the failure surfaced on a test that had nothing to do with it. A flaky test is worse than no test. Now suspend, and run three times to confirm.

### Tested

169 unit tests pass and lint is clean. Checked on an **Android 8.0 (API 26)** emulator with seeded tasks and events: the task progress bar, the gold holiday card with its Constitution Day and Pchum Ben countdowns, the quick-action row in its category colours, and compact density. The minified release build was launched separately.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

## 1.4.0 — 2026-09-08

A visual identity, and the design system underneath it.

### A dark app that was designed dark

The dark theme was Material's default greys with the accent dropped in. It is now its own palette — a near-black with a blue bias, panels a step above it, and a hairline that separates a card without drawing a box around it. Light mode is designed as its own thing rather than an inversion: the neon accent is pulled down until it can carry white text, instead of being reused at a lightness that only works on black.

### The accent now reaches the whole app

`schemeFor()` filled thirteen of Material's colour roles and left the rest to the baseline — which is a purple. So the navigation bar's selected pill was baseline lavender sitting underneath a green accent, and Quick Actions asked for `tertiary` and got baseline pink. Neither had anything to do with the colour you had chosen.

Every role is filled now, from one accent.

### Green, cyan, magenta — from whichever colour you pick

The identity wants three colours travelling together. Hard-coding cyan and magenta would have left the other nine accent choices sitting next to colours from somebody else's palette.

So the partners are rotated off your accent's own hue. The new default — **បៃតងណេអុន**, neon green — produces very nearly the intended cyan and magenta. Pick **ស្វាយ** and you get a violet and a lime that belong to *it*. One rule, ten coherent palettes, and the navigation pill, the chips and the AI panel all follow.

### ខែណា? · The month screen never said which month it was

It opened straight into the weekday captions and the grid. After two swipes there was nothing anywhere on the screen that told you which month you were looking at — the single most important label a month view has. The day numbers cannot tell you; every month has a 14th.

There is a header now, with arrows as well as the swipe, and a **ថ្ងៃនេះ** that appears only when you are not already on this month.

### The countdown holds still

The timer redraws every second, and its digits were proportional — so "០១:៤៨:២២" was a different width from "០១:៤៨:២៣" and the whole card shifted sideways once a second. It uses tabular figures now, so it stays where it is.

There is a day-progress bar under it, and **ការងារនៅសល់ថ្ងៃនេះ** has moved off the bottom-right corner of the card, where the floating action button was sitting on top of it.

No futuristic display face for it, deliberately: Orbitron and the fonts like it have no Khmer coverage at all, so the digits would have rendered in one face and the Khmer label beneath them in another. The timer earns its character from size, weight and figure spacing, which work in both scripts.

### ទិដ្ឋភាពថ្ងៃនេះ · One card that says what day it is

The Gregorian date, the weekday, the Khmer lunar date, today's holiday and a live clock now sit together on the card at the top, instead of being spread across three cards you had to assemble mentally as you scrolled. The lunar date in particular was three positions down — the wrong place for the thing that makes this a Khmer calendar.

### A card system

`SurfaceCard`, `HeroCard`, `GradientCard`, `TechCard`, `StatusBadge`, `MetricTile`, `SectionTitle`, `IconTile`, `PrimaryFab`. Before this a card was a `Card` on one screen and a clipped `Box` on the next, with paddings of 10, 12 and 16dp on screens that sit beside each other in the navigation bar — near-identical surfaces, which reads worse than surfaces that are obviously different.

The angular cut-corner shape is reserved for surfaces that report on the machine rather than on your day — the AI panel — so that it keeps meaning something. The calendar itself stays rounded.

### Smaller things

The navigation bar shows every label rather than only the selected one; five Khmer words fit, and an icon-only tab asks you to learn what a sparkle means before you can find the assistant. The floating action button is the accent instead of a dark container tint. The empty state has a compact form for short panels — on the month screen it was putting its icon on screen and both lines of explanation below the fold, so a first launch showed one grey glyph and nothing else.

### Tested before release

169 unit tests pass and lint is clean. The redesign was checked on an **Android 8.0 (API 26)** emulator in dark mode, in light mode, and at **1.3× system font scale** — where the Khmer lunar date wraps to two lines rather than clipping and the dashboard scrolls rather than dropping anything. The minified release build was launched and exercised separately from the debug one.

### What has not changed

Every setting you had. The accent chooser still offers ten colours and every one of them still produces a complete scheme. Khmer numerals, the date format, the first day of the week, dashboard card order and visibility, work schedule, widgets — untouched.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the device. The AI is optional, off by default, and runs entirely on-device.

## 1.3.2 — 2026-09-08

Seven fixes, from continuing two lines of the audit: settings that nothing reads, and side
effects that can take the app down with them.

### The availability hours drove nothing

**ការកំណត់ → រូបរាង → ម៉ោងធ្វើការ** has a start and an end hour, and the line underneath said they
were used for free-time suggestions and the week display. Every reader of those two values was
the settings screen editing its own copy. Free-slot search used a hard-coded 08:00–18:00 and
the day ruler a hard-coded 06:00–22:00.

Both read the setting now. That is the sixth setting found in this audit that was written and
never read, and the first whose description made a claim about what it did.

### "When am I free today" answered with this morning

Asked at three in the afternoon, it offered 08:00 onwards — seven hours already gone. The
window is now trimmed at the current moment, and once the day's hours are over it says so
instead of returning an empty answer.

### A timed event outside 06:00–22:00 was invisible in the day view

It appeared in the month grid, the agenda, the dashboard, search and the widgets, and was
simply absent from the one screen that lays a day out hour by hour. The ruler now covers your
own hours *stretched to include every event on the day*, so nothing can fall outside it.

This became easy to reach in 1.3.1, which fixed the editor so an event added late in the
evening correctly starts at midnight — and then that event could not be seen here.

### An event clashed with itself

An event that runs past midnight is carried onto the second day so it shows in both grids, and
the clash finder read the two rows as two events: **Late 11pm ⟷ Late 11pm — ជាន់គ្នា ៦០ នាទី**.
Found by running the thing, not by reading it.

### Two assistant replies printed ISO dates

`2026-09-08`, in Latin digits, mid-sentence, inside otherwise entirely Khmer text. Both now
read **ថ្ងៃអង្គារ ទី៨ ខែកញ្ញា**.

### `.ics` import corrupted times and lost reminders

Four separate defects in the file everyone else's calendar speaks:

- A `TZID` parameter was parsed off the date and thrown away, so a 09:00 meeting exported from
  New York arrived as 09:00 in Phnom Penh — eleven hours out.
- `TRIGGER;VALUE=DURATION:-PT15M` and `-P1D`, which is what Google Calendar writes for a
  day-before reminder, matched nothing. The reminder vanished without a word.
- `-PT0M` — this app's own "at the time" reminder — was discarded for not being a positive
  number, so it was lost on a round trip through a file the app had written itself.
- Importing the same file twice silently doubled every event in it. Duplicates are now skipped
  and counted in the message, so you are told which of the two happened.

The writer also never folded lines at the 75-octet limit RFC 5545 sets. Khmer is three bytes
per character, so a twenty-five character description already overran it, and strict parsers
reject the file rather than guessing.

### Arming alarms could kill the app

Thirteen places re-arm reminders, and eleven of them do it as a side effect of something else
you asked for: saving an event, deleting one, restoring a backup, changing a notification
setting, finishing a boot. None had an exception handler, so a failure to arm an alarm did not
fail the arming — it ended the process, and from `BOOT_COMPLETED` it ended it at boot.

Failing to set a reminder is bad. Losing the calendar because a reminder could not be set,
while you were doing something else entirely, is worse.

### Colour choices were unusable with a screen reader

The category circles in the event editor and the accent circles in Appearance were bare
coloured dots with a tap handler: 32 and 34 dp, under the 48 dp minimum, and carrying no label
at all. A screen reader met a row of identical unnamed nodes. Only the *selected* one was ever
announced, which is backwards — you need the labels in order to choose.

They look exactly the same. Each now announces its name — **ខៀវ**, **ស្វាយ**, **បៃតង** — reports
whether it is chosen, and sits in a 48 dp target.

### Tested

169 unit tests pass, up from 130, and lint is clean. Everything above was checked on an
Android 8.0 (API 26) emulator, with the clock moved to the hour that shows each one: the day
ruler stretched to 04:00 and 23:00 around two events that used to be invisible, the free-time
answer at 15:30, the clash list, and the touch targets measured at 126 px on a 420 dpi screen —
exactly 48 dp.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the
device. The AI is optional, off by default, and runs entirely on-device.

## 1.3.1 — 2026-09-07

A patch release, from continuing to audit settings that are written and never read. Three of
the four fixes below come from that one question: *does anything actually read this?*

### No event could be created after 11pm

A new event opens at the next whole hour. That was computed on a clock time with no date
attached, and a clock time wraps silently at midnight.

At 23:20, "the next whole hour" came out as 00:00 — and, keeping today's date, the editor
opened on a slot twenty-three hours in the past. Between 22:01 and 23:00 it was worse: the
editor opened 23:00 to 00:00, and its own validation then read that end as earlier than the
start and refused to save, with no way forward except editing both times by hand.

The arithmetic now carries the date, so 22:51 gives 23:00 today to midnight tomorrow, and
23:20 moves the whole event to tomorrow morning. Seven tests pin the awkward hours: 22:51,
23:20, exactly on the hour, a date picked from the calendar grid, and an eight-hour default
duration that lands the following morning.

Verified on the emulator at 22:57, which is exactly the minute that could not be saved before.

### The editor and the database disagreed about midnight

Storage has always treated an end at or before the start as an event running into the next
day. The editor refused to save one. The editor now allows it and says so — **បញ្ចប់នៅថ្ងៃបន្ទាប់**
appears under the end row — so a 10pm-to-2am shift is one event rather than a rejection, and
a mis-tapped end time is never a silent 23-hour event.

### The date format setting did nothing

**ការកំណត់ → រូបរាង** has offered a choice of three date orders since 1.1.0. Nothing in the app
read the setting. The function that would have applied it had no callers at all — you could
pick ថ្ងៃ/ខែ/ឆ្នាំ, watch the radio button move, and every date in the app stayed exactly as it
was.

It now reaches the event editor's start and end rows, the event detail headline, the day
headline shared by the month and week screens, and the search results. Each choice in
Appearance shows today's date rendered that way, so you can see the difference before picking
one.

There is a fourth choice now, **ថ្ងៃ ខែ ឆ្នាំ (ខ្មែរ)**, and it is the default. It is the written
Khmer form every screen already drew, so anyone who never opened this setting sees nothing
change; anyone who did pick a numeric order finally gets it.

One detail worth naming: the Khmer month name is looked up, not formatted. A date pattern's
`MMMM` resolves against the phone's locale, so on a device set to English it would have
printed "September" in the middle of an otherwise entirely Khmer screen. There is a test that
sets the JVM locale to US and to France and checks the month stays មករា.

### Searching for "%" returned your whole calendar

`%` and `_` are wildcards to SQLite's `LIKE`. Typed into the search box they went through
unescaped, so `%` matched every event and `_` matched any single character. Both are now
literal, and Khmer substring search — which is why this is a `LIKE` scan and not full-text
search in the first place — is unchanged.

### Tested

130 unit tests pass and lint is clean. Everything above was checked on an Android 8.0 (API 26)
emulator: switching the date format to dd/MM/yyyy and back to Khmer, the live previews in
Appearance, and creating and saving an event at 22:57.

### Privacy

Unchanged: no account, no analytics, no tracking, no ads. Your calendar never leaves the
device. The AI is optional, off by default, and runs entirely on-device.

## 1.3.0 — 2026-09-07

### The dashboard has a top

The Home tab opened straight into a date card. It now opens with a greeting that matches the
time of day — អរុណសួស្តី, ទិវាសួស្តី, សាយណ្ហសួស្តី, រាត្រីសួស្តី, using the same boundaries as the
Khmer parts of the day, so the greeting can never disagree with the line beneath it — the
date, a live clock, and what is next.

"What is next" prefers the next event as a relative time: **Backup-Roundtrip-Test ·
ក្នុងរយៈពេល ៤៩ នាទី**. A count of today's events is what is left to say when nothing is coming.
All-day entries are excluded, because "in 40 minutes" is meaningless for something with no
clock time and the dashboard would otherwise announce a birthday as though it were starting.

Its gradient is a low-alpha wash of the accent the user chose, not a second saturated block.
The work card's colour is doing a job — green means working, amber means a break — and two
competing blocks of colour mean neither reads as a signal.

### Fixed: three settings that did nothing

**Loading states did not exist.** `isLoading` was set on both the dashboard and the calendar
and read by nothing, so the first frame after a cold start was an *empty state* —
គ្មានព្រឹត្តិការណ៍ថ្ងៃនេះ — that corrected itself a moment later. Telling someone they have
nothing on and then taking it back is worse than saying nothing, because the empty state is a
claim. Skeleton placeholders now hold the layout until the data arrives.

**Work notification settings took effect tomorrow.** Alarms were armed at process start and by
the daily worker, and nowhere else. Turning notifications on, editing a shift, or changing the
warning time did nothing at all until the app was next launched. The settings are now observed
the way widget data already was — watch the state, not each write path — so the settings
screen, a restored backup, and anything added later all reach it, and none of them has to
remember to.

**Work alarms could never be cancelled.** Cancellation built a bare `Intent` while the armed
one carried a `data` URI, and `data` counts toward `Intent.filterEquals` — so `FLAG_NO_CREATE`
never matched and every alarm survived. The set of armed request codes also lived only in
memory, which put yesterday's alarms permanently out of reach after a restart. The practical
effect: **turning work notifications off left them firing.** The URI is now derived from the
request code, which makes it reproducible from nothing, and cancellation sweeps the whole code
range.

**One screen counted in Latin.** The event editor carried its own copy of the reminder label
that never learned about Khmer numerals, so it showed "10 នាទី" while notification settings —
the same information — showed "១០ នាទីមុន".

### New: a warning before the shift changes

Optional, off by default, and configurable at 5, 10, 15 or 30 minutes in
**ការកំណត់ → កាលវិភាគការងារ**. Off by default because a warning doubles the day's notifications,
and these share a notification channel with event reminders — a countdown that announces itself
too often gets the whole channel silenced.

### Verified on device

On an Android 15 emulator, against this build:

- The alarm fix, by cycling the setting and reading the armed alarms back out of the system:
  30 min → 17:00 + 17:30; 15 min → 17:15 + 17:30; off → 17:30 only; 5 min → 17:25 + 17:30;
  notifications off → none. Previously the stale alarms simply accumulated.
- The live re-arm, by changing the setting without restarting the app.
- **A full backup round-trip**: export to JSON (title, location, UTC millis, zone and reminder
  all faithful), wipe all app data, restore — the event came back. Restore states that it adds
  to existing data rather than replacing it, and asks first.
- **A full `.ics` round-trip**: export produced valid RFC 5545 with `DTSTART` correctly
  converted to UTC and the reminder as `TRIGGER:-PT30M`; importing it back created the event.
- The greeting, clock, next-event line and skeletons.
- Both widget providers registered, and a widget update with no crash.

## 1.2.0 — 2026-09-07

### The assistant understands a pasted announcement

Before this release the assistant could read a short command — "ថ្ងៃស្អែក ម៉ោង ២ រសៀល ប្រជុំ" —
but not a paragraph. Given one it produced a title that was the whole paragraph, because the
title was built by deleting the date and time from the input and keeping whatever was left.

`LongTextEventExtractor` treats a paragraph as a paragraph: it splits the text into
sentences, scores each for event signal, parses only the one making the announcement, and
builds the title *around the event noun* instead of from the leftovers. Everything else
becomes the description.

Pasting the announcement below now yields the title កិច្ចប្រជុំប្រចាំសប្តាហ៍, tomorrow's date,
2:00 PM, the room បន្ទប់ប្រជុំ A, and the discussion as the description:

> សួស្តីក្រុមការងារ នៅថ្ងៃស្អែកយើងនឹងមានកិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង ២ រសៀល នៅបន្ទប់ប្រជុំ A
> ដើម្បីពិភាក្សាអំពីផែនការការងារប្រចាំខែ …

Nothing is invented. "ប្រជុំជាមួយក្រុមការងារថ្ងៃស្អែក" gets tomorrow's date and **no time at
all** — the proposal says ម៉ោងមិនបានបញ្ជាក់ rather than guessing 9:00. Every field the text did
not state is listed back to the user instead of filled in.

The extractor is pure and needs no model: it costs microseconds, works on a device that could
never host a language model, and cannot hallucinate. When a model *is* loaded it is asked as
well, and its answer is validated — title capped at eight words, dates outside a sane window
rejected, malformed output discarded — before it can reach the screen.

Long text is handled by scoring sentences and narrowing to the region around the strongest
one, so a meeting buried in a long email does not take its date from an unrelated line three
paragraphs away. Input is capped rather than allowed to grow without bound.

Text can now reach the assistant by **Share** and **Process Text** from any other app, which
is how a pasted announcement actually arrives.

### Fixed: a whole class of bug caused by Khmer word spacing

Khmer is written without spaces between words. "កិច្ចប្រជុំប្រចាំសប្តាហ៍នៅម៉ោង" is a single
whitespace token holding a noun, a preposition and the word for "hour", so every "take the
next four words" heuristic in the parser silently swallowed an entire clause.

Two visible symptoms, one cause:

- Event titles ran on into the meeting time.
- The room in a pasted announcement was lost. The sentence contains នៅ three times; the first
  introduces a date and the second a time, and only the third is a place. The parser took the
  first one and produced a "location" so long it was then discarded.

`TextBoundaries` cuts by character index at the next marker of a *different kind* — a time, a
date, a place, a person, a purpose — and every affected site now uses it. Latin markers are
only matched when the lexicon already spaces them out, so an English title is not cut in half
at "May" or "at".

### Fixed: Khmer numerals came back as Latin ones

The parser matched against its own ASCII-normalised working string and then sliced values out
of *that*, so a room written "សាលប្រជុំធំជាន់ទី ៣" came back as "…ទី 3" in a calendar that is
otherwise entirely in Khmer numerals. Matching still runs on ASCII digits; the two views are
now index-aligned, and anything handed back to the user is sliced from the string they typed.

### New: the work-time countdown

A live countdown for the working day, on the home dashboard. It knows six states — before
work, working, break, finished, day off, disabled — and shows the time left in the current
block, what comes next and when, the total left today, and a progress ring.

Defaults match an ordinary Cambodian office week: Monday to Friday 7:30–11:30 and
13:30–17:30, Saturday 7:30–11:30, Sunday off.

Every day is editable in **Settings → កាលវិភាគការងារ**, including how many blocks it has: a
shop that opens straight through and a teacher with three separate sessions are equally
ordinary, and neither is expressible if the screen only offers "morning" and "afternoon".
Inverted ranges are refused rather than saved.

Optional notifications at each shift change, off by default. They are inexact alarms on
purpose — a shift notice a minute late costs nothing, and an exact alarm would spend a
battery-relevant resource that event reminders need more.

The card only ticks while it is on screen and the app is in the foreground, and stops
entirely on a day with nothing left to count down to.

### The dashboard is now yours to arrange

Nine cards — today, work, Khmer lunar date, upcoming events, holidays, countdowns, tasks,
today's note, quick actions — each of which can be hidden and moved. A saved order from an
earlier version is kept, and cards added by a newer version are appended to it rather than
replacing it.

Quick actions adds a row for the five things people open the app to do: new event, new task,
new note, assistant, search.

### Fixed: two dialogs that ignored the theme

A platform dialog inherits the *Activity's* theme, and the Activity theme is a fixed light one
because every pixel after the first frame is drawn by Compose. So a user with the app set to
dark got a white date or time picker, and everyone got Material's default teal on a screen
with no teal anywhere else. A `values-night` resource cannot fix it either — that follows the
*system*, and the app's own light/dark setting may disagree with it.

The theme is now chosen in code from the same flag the Compose theme uses, and the buttons are
tinted with the user's live accent.

Separately, the event time picker asked the *device* for 12- or 24-hour rather than the app's
preference, so choosing a clock format in settings changed every time in the calendar except
the one being typed into.

### One shape scale for the whole app

`MaterialTheme.shapes` was never set, so Material's own cards, chips and dialogs rounded to
their defaults while hand-drawn surfaces used ten different radii — on the same screens.
`Tokens.kt` holds the scale that was missing (spacing, radius, elevation) and the theme feeds
the radii to Material so both halves of a screen agree. Gradients moved into the theme under
names that say what they mean — working, resting, done — rather than what colour they are.

### The AI download crash fix moved to where it is enforced

The `android:foregroundServiceType="dataSync"` declaration that fixed the 1.1.0 crash now
lives in `:ai-core`, beside `ModelDownloadWorker`, the only thing that needs it. In the app
module it was correct but unenforced; next to the worker, lint's
`SpecifyForegroundServiceType` check verifies the pair, so the declaration cannot drift away
from the code that depends on it. The merged manifest is unchanged.

### Verified on device

On an Android 15 (API 35) emulator, against this build:

- Both pasted announcements from the specification, end to end through Share.
- A full model download (547 MB), model load and on-device inference — process alive
  throughout, no `FATAL EXCEPTION`.
- The work countdown ticking, with its arithmetic checked against the clock.
- The work schedule screen: display, edit, add a block, reset, and persistence across a
  restart.
- Dashboard reorder and hide.
- The date and time pickers in dark mode.
- **An upgrade from 1.1.0 in place**: an event with its title, times and location survived,
  and so did a customised set of hidden dashboard cards — with the two new cards appended
  rather than replacing them.

## 1.1.0 — 2026-09-07

### Fixed: the app closed when downloading an AI model

**Root cause.** `ModelDownloadWorker` called `setForeground()` with
`FOREGROUND_SERVICE_TYPE_DATA_SYNC`, but WorkManager's `SystemForegroundService` — which the
app inherits from the library's manifest — declared no `foregroundServiceType`. From
Android 14 the system rejects that mismatch:

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: foregroundServiceType 0x00000001 is not a subset of
foregroundServiceType attribute 0x00000000 in service element of manifest file
    at androidx.work.impl.foreground.SystemForegroundService.startForeground
```

It is thrown inside `Service.onStartCommand` **on the main thread**, so the worker's
`runCatching { setForeground(info) }` could never catch it — different thread, different call
stack. The process died instantly, which is what the user saw as "the app closes by itself".

**Fix.** `AndroidManifest.xml` re-declares WorkManager's service with
`android:foregroundServiceType="dataSync"` via `tools:node="merge"`.

**Verified** on an Android 15 (API 35) emulator: reproduced the crash on the old build,
confirmed zero `FATAL EXCEPTION` on the new one, and ran a real 547 MB download to completion.

### Hardened the whole download path

Beyond the crash, the download had failure modes that produced a spinner that never ended:

- **Bounded retries.** `Result.retry()` had no ceiling, so a download that could never succeed
  rescheduled with backoff forever while the UI showed "running". Now four attempts, and only
  for genuinely transient failures.
- **Typed failures.** `DownloadFailure` — `NETWORK`, `STORAGE_FULL`, `SERVER`, `CORRUPT`,
  `CANCELLED`, `UNSUPPORTED`, `UNKNOWN` — each with a Khmer message and the action that
  actually resolves it. A retry button against a full disk is a loop, not an action, so
  `STORAGE_FULL` offers to free the partial file instead.
- **Storage preflight**, and a full disk detected mid-write is now terminal rather than retried.
- **Foreground promotion is optional.** If the system refuses it — background-start
  restrictions, OEM policy, Android 15's data-sync quota — the transfer carries on.
- **`getForegroundInfo()` can no longer throw**; it falls back to a notification built inside
  `:ai-core` rather than failing the job.
- **`ExistingWorkPolicy.KEEP`, not `REPLACE`.** Tapping download twice used to cancel the
  running transfer and start again from the same byte.
- **Throttled progress** to about 1.4 updates/second instead of one `setForeground` per percent.
- **Validation before install.** The `.part` file is only renamed into place after its length
  matches the published size.

### Download and model-management UX

- **Confirmation before download**, showing file size, remaining bytes when resuming, and the
  RAM the model needs. Mobile data is metered; a 2 GB download from a mis-tap is a real cost.
- **Live progress** with percent, downloaded/total, speed and estimated time remaining.
- **Pause / resume / cancel-and-discard.** Pause keeps the partial file; resume continues from
  the exact byte via a Range request.
- **Per-model state**: installed, partially downloaded (with how far), or damaged — each with
  the right buttons.
- **Actionable error card** naming the failure, confirming calendar data is untouched, and
  offering retry / discard.
- All numbers in these screens now follow the Khmer-numeral preference; the progress line used
  to read `៣% · 21 MB`, mixing scripts mid-sentence.

### Removed a false integrity check

An earlier version of this work added a magic-byte check on downloaded bundles. Testing
against the real files showed it was wrong: Qwen `.task` bundles are a length-prefixed zip
(`PK` at offset 4) and the Gemma one is a length-prefixed TFLite flatbuffer (`TFL3` at
offset 4). The check rejected correctly downloaded models as damaged. Removed — a check that
fails valid input is worse than no check. Length validation against the exact published
`Content-Length` remains, and `docs/AI_MODELS.md` explains what that does and does not catch.

### Monday is the first day of the week

- The default is now Monday (Cambodian and ISO-8601 practice), changeable to **any** of the
  seven days rather than the previous shortlist of three.
- New `CalendarWeek` in `:calendar-core` is the single source of week arithmetic, used by the
  month grid, week view, agenda, week numbers, both widgets and the recurrence engine.
- **Fixed a real correctness bug:** `RecurrenceExpander` hard-coded Sunday as the week start
  while the UI read the preference, so a fortnightly event could expand to different dates than
  the grid drew. `RecurrenceRule` now carries RFC 5545 `WKST`, which round-trips through the
  `.ics` export and is only emitted when it can change the expansion.

### Two settings that did nothing now do something

- **Week numbers** was persisted and toggleable but read by no screen. The month grid now draws
  a week-number gutter, numbered from the user's own first day of the week with a four-day
  minimum — ISO-8601 numbering under the Monday default.
- **Holiday notifications** was likewise dead. Public holidays now notify at 18:00 the evening
  before, when there is still time to change plans. Observances are deliberately excluded.

### New customisation

- **12/24-hour clock** — system, 12-hour or 24-hour. The 12-hour form uses the Khmer parts of
  the day (`២ រសៀល`) rather than AM/PM.
- **Date format** — D/M/Y, Y-M-D or M/D/Y.
- **Working hours**, used by free-slot suggestions.
- All time rendering now goes through one `CalendarFormats`. It was previously nine copies of
  `"%02d:%02d".format(...)` across screens, notifications, widgets and the assistant — which is
  why a clock preference could not be added without finding all nine.

### Application updates

New update screen under Settings → About:

- Shows the installed version, checks a `version.json` manifest in the project repository,
  and displays the changelog for a newer release.
- Downloads with progress, then **verifies** the file: length, an optional published SHA-256,
  and — the check that matters — that the APK's signing certificate matches the installed
  app's. A mismatched signature is refused before the user reaches Android's installer with an
  error they cannot act on.
- Installation goes through the platform installer with its own confirmation. Nothing is
  installed silently, and nothing is checked in the background: the request happens when the
  user opens the screen or taps the button.
- `REQUEST_INSTALL_PACKAGES` and a `FileProvider` scoped to `cache/updates` only.

### Other fixes

- `NotificationSettingsScreen` used the deprecated `getParcelableExtra`; now `IntentCompat`.
- `androidx.biometric:1.1.0` resolved fragment to 1.2.x, predating the ActivityResult APIs
  `MainActivity` uses; fragment is pinned to 1.8.6. This was a lint-fatal error.
- `backup_rules.xml` / `data_extraction_rules.xml` excluded `models/` from a domain that never
  included it — a lint-fatal no-op, removed.
- Glance widgets only redrew on the system's 30-minute cycle; the Application now watches the
  Room tables and redraws on any data change, debounced.
- `MediaPipeEngine` suppresses the `LlmInference` deprecation with a note pointing at the
  `AiEngine` seam that contains a future migration.

### Tests

67 unit tests, all passing.

- New `CalendarWeekTest` (8): week start, grid alignment across every start day, ISO week
  numbers, weekend definition, preference fallback.
- New `WeekStartRecurrenceTest` (4): `WKST` invisible at interval 1, decisive at interval 2,
  round-tripping through the RRULE, defaulting to Monday per RFC 5545.
- New `ModelIntegrityTest` (7): partial vs damaged vs missing, over-long files, failure-name
  round-trips, progress bounds.

### Privacy — unchanged

No account, no analytics, no tracking, no cloud AI, no API keys, no calendar data leaving the
device. Network is used in exactly three places, all user-initiated: the model download, the
update check, and the update download.

---

## 1.0.0 — 2026-09-07

First release. Khmer calendar with Chhankitek lunar dates, Cambodian holidays, events,
reminders, recurrence, tasks, notes, widgets, backup/restore, `.ics` interchange, and an
optional on-device assistant.
