# Honkfilter

A chat filter for Paper servers that blocks bad language without censoring innocent words, and lets you tune every rule from the config.

## Features

- Matches whole words by default, so `class`, `assassin`, `Scunthorpe` and `cockpit` are never touched.
- Catches sneaky spellings like `fuuuuck`, `sh1t`, `a$$`, `f u c k` and `f.u.c.k`.
- Three ways to handle a bad word: censor it, block the message, or shadow it so only the sender sees it.
- Censored words are replaced with `honk` by default. Change the replacement word (or a list to pick from at random), or switch back to `****`.
- Optional partial rules for words that must be caught inside other words, with a whitelist to keep real words safe.
- Extra filters for too many capitals, stretched out characters, chat spam, repeated messages and links.
- Filters private message commands like `/msg` too.
- Tells staff when someone trips the filter, and can run punishment commands after repeat offenses.
- Add, remove and test words in game with `/honkfilter`, and reload the config without a restart.

## Setup

1. Stop the server.
2. Drop `Honkfilter-1.0.0.jar` into the `plugins` folder.
3. Start the server once to generate `plugins/Honkfilter/config.yml`.
4. Edit the config (see below), then run `/honkfilter reload` or restart.

Requires Paper 26.3 and Java 25.

## Commands

| Command | What it does |
| --- | --- |
| `/honkfilter reload` | Reloads `config.yml` without a restart. |
| `/honkfilter add <words\|partials\|whitelist\|regex> <word>` | Adds an entry to a list and saves the config. |
| `/honkfilter remove <words\|partials\|whitelist\|regex> <word>` | Removes an entry from a list. |
| `/honkfilter list <words\|partials\|whitelist\|regex>` | Shows what is in a list. |
| `/honkfilter test <text>` | Shows what the filter would catch and what the message would look like. |

`/hf` works as a shorter alias.

## Permissions

| Permission | What it allows | Default |
| --- | --- | --- |
| `honkfilter.admin` | Use the `/honkfilter` command. | OP |
| `honkfilter.notify` | See staff alerts when someone trips the filter. | OP |
| `honkfilter.bypass` | Skip every filter. | Nobody |
| `honkfilter.bypass.words` | Skip the bad word filter. | Nobody |
| `honkfilter.bypass.links` | Skip the link filter. | Nobody |
| `honkfilter.bypass.caps` | Skip the caps filter. | Nobody |
| `honkfilter.bypass.repeats` | Skip the repeated character filter. | Nobody |
| `honkfilter.bypass.spam` | Skip the cooldown and repeated message filter. | Nobody |

Nobody has the bypass permissions by default, not even OPs, so you can test the filter on yourself. Give them to staff with your permissions plugin.

## Config

```yaml
words:
  # censor = swap the bad word for the censor character and still send it
  # block  = don't send the message at all
  # shadow = only the sender sees it, nobody else does
  mode: "censor"
  # replace = swap the word for a replacement word, mask = swap every letter for the censor character
  censor-style: "replace"
  # If you list more than one, a random one is picked each time.
  replacement-words:
    - "honk"
  censor-character: "*"
  warn-player: true

  # Matched as whole words only, so "class" is never touched.
  blocked-words:
    - "fuck"
    - "fucking"
    - "shit"

  # Matched anywhere inside a word. Use sparingly.
  blocked-partials:
    - "fuck"

  # Advanced: regular expressions.
  blocked-regex: []

  # Words that are always fine. Skipped by the partial rules.
  whitelist:
    - "class"
    - "assassin"

  # Endings allowed on blocked words, so "fucks" still counts.
  allowed-suffixes:
    - "s"
    - "es"

  repeated-letters: true
  leet-speak:
    enabled: true
    replacements:
      "0": "o"
      "$": "s"
  spaced-letters:
    enabled: true
    minimum-letters: 3

caps:
  enabled: true
  minimum-length: 8
  max-percent: 70
  action: "lowercase"

repeated-characters:
  enabled: true
  max-allowed: 4

spam:
  enabled: true
  cooldown-seconds: 1.0
  block-repeated-messages: true
  repeat-window-seconds: 10

links:
  enabled: false
  action: "block"
  allowed-domains: []

commands:
  enabled: true
  list: [ "msg", "tell", "w", "whisper", "r", "reply", "me" ]

punishments:
  enabled: false
  window-seconds: 300
  steps:
    "3":
      - "tempmute {player} 10m Watch your language"
```

The full file has every option, every message and a comment on each setting. Messages use [MiniMessage](https://docs.advntr.dev/minimessage/format.html) colors.

## FAQ

### How do I make censored words show up as honk, or something else?

Any word you add to `blocked-words` is replaced with the first entry of `replacement-words`, which is `honk` by default. Change that list to use your own word, add several to pick one at random, or set `censor-style: "mask"` to get `****` instead. An ALL CAPS bad word gets an ALL CAPS replacement.

### Why was a word I added not caught?

Blocked words only match whole words. If you add `fuck` it will not catch `fucking`, so add every spelling you want blocked. If a word must be caught inside other words, add it to the partials list and put any real words that contain it on the whitelist.

### Why is a word I did not mean to filter getting caught?

Run `/honkfilter test <text>` to see which rule caught it. If it came from a partial rule, add the real word to the whitelist. If it came from a blocked word, check `allowed-suffixes`, since longer endings can make innocent words match.

### I am an OP and my messages are filtered. Is that a bug?

No. Nobody bypasses the filter by default so you can test it. Give yourself `honkfilter.bypass` to skip it.

### What happens if I make a mistake in the config?

A bad regular expression or an unknown option value is skipped with a warning in the console and in the reload message, and the plugin falls back to a safe default. Chat is never broken by a filter error.
