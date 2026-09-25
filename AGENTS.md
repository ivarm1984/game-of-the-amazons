# Agent instructions

## Privacy: never expose the owner's personal email

This repository is public. The owner's personal email address must never
appear in commits, files, or anything pushed to GitHub.

- Author and commit only as `Ivar Mällas <5469913+ivarm1984@users.noreply.github.com>`
  (GitHub's private noreply address). The repo's local `user.email` is set to
  this; don't override it with the global git config or a `--author` flag.
- Before committing, check `git config user.email` returns the noreply address.
- Never write a personal email address into source, docs, config, commit
  messages, or PR descriptions.
