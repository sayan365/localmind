---
version: alpha
colors:
  canvas: "#F7F8F4"
  surface: "#FFFFFF"
  input: "#F0F3F1"
  divider: "#D8DEDA"
  ink: "#121513"
  muted: "#65706B"
  action: "#1D5D50"
  information: "#3B82F6"
  success: "#126B49"
  warning: "#81530C"
  danger: "#C44242"
typography:
  body:
    fontFamily: "sans"
    fontSize: "15sp"
    lineHeight: "1.1"
  title:
    fontFamily: "sans"
    fontSize: "19sp"
    lineHeight: "1.2"
rounded:
  control: "7dp"
  surface: "8dp"
spacing:
  compact: "6dp"
  control: "10dp"
  section: "16dp"
components:
  button:
    minHeight: "44dp"
  iconButton:
    size: "48dp"
  dialog:
    owner: "Android AlertDialog"
---

# LocalMind Design

## Overview

LocalMind is a quiet, work-focused phone assistant for repeated daily use. The interface
should feel like a trustworthy Android utility: direct, compact, and legible. The visual
signature is the restrained dark-green user/action color against a near-white canvas.
Avoid marketing layouts, decorative illustration, oversized headings, and card stacks.

## Colors

Runtime colors in `androidApp/src/main/res/values/colors.xml` are canonical. This document
names their durable semantic roles. Blue is informational, green confirms local/ready
states, amber indicates setup or waiting, and red is reserved for destructive or failed
states. Do not use color as the only status signal.

## Typography

Use Android's system sans family. Body copy is 15-16sp; compact labels are 10-12sp;
screen titles are 18-20sp. Use bold only for hierarchy and key values. Letter spacing is
zero. Assistant text must remain selectable.

## Layout

Use a 16dp content inset and a compact vertical rhythm. The chat remains the primary
screen. Secondary data controls belong in the existing overflow menu and native dialogs,
not in a permanent dashboard. Controls keep stable dimensions and 44dp minimum targets.

## Elevation & Depth

The app is mostly flat. Use dividers, fill contrast, and native modal elevation. Do not add
decorative shadows or floating section cards.

## Shapes

Use 7-8dp radii for controls and contained surfaces. Avoid pills except small status badges.

## Components

Use native Android buttons, text fields, lists, and dialogs. Destructive dialogs name the
stored item and focus cancellation through Android's standard behavior. Empty states say
what is empty and how an item is created.

## Do's and Don'ts

- Do keep consequential actions explicit and reversible where possible.
- Do show whether data is local and whether cloud AI is disabled.
- Do preserve text selection and Android accessibility semantics.
- Don't claim a reminder was delivered when it was only scheduled.
- Don't expose permissions or stores that are not implemented.
