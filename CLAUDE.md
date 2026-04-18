# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository status

This repo is a fresh scaffold. `backend/`, `frontend/`, and `shared/` exist but are empty — there is no build system, no package manifest, no test runner, and no source code yet. Do not invent commands; ask the user (or check newly-added manifests) before claiming a build/test/lint command exists. The intended split is backend code under `backend/`, frontend code under `frontend/`, and code shared between them under `shared/`.

## Working with data sources

Never guess database object names (schema, table, column). Verify them from code in the repo before using them. If you cannot verify, say so explicitly in your output rather than assuming.

## Java conventions (when Java code is added)

- Do not add Javadoc by default. Only add Javadoc to a specific method when its logic is genuinely complex enough to warrant explanation — never to whole classes for the sake of it.
- Lean on meaningful names instead of comments.
- Match the existing code style of whatever module you are editing.
