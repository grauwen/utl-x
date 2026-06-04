# UTL-X Books

Source for the UTL-X book series. **One directory per book**; sources are [Typst](https://typst.app).

| Directory | Book | Status |
|-----------|------|--------|
| `language/`     | *UTL-X — One Language, All Formats* | active |
| `engine-azure/` | *UTLXe on Azure*                    | active |
| `engine-gcp/`   | *UTLXe on GCP* (future)             | planned |
| `engine-aws/`   | *UTLXe on AWS* (future)             | planned |
| `cookbook/`     | *UTL-X Cookbook* (future)           | planned |
| `_shared/`      | shared Typst templates / styles / diagrams | scaffold |

## Building
Each book has its own `build.sh` (run it from inside the book's directory, e.g.
`cd books/language && ./build.sh`). The current entry point is `main.typ`.

> **Note:** the published PDFs are currently committed at each book's root
> (legacy). The intended convention is compiled output under `dist/` (already
> gitignored via `**/dist/`), with only the released PDF committed to `main` —
> migration of the existing PDFs is a follow-up.

## Website
`language/` is the source for the **utlx-lang.org** site (language reference +
book). The 652-function stdlib reference comes from the existing Markdown in
`docs/stdlib/`. Pipeline (Typst → Markdown → static site) is set up under the
website work — see the website strategy doc.

## Conventions
- One directory per book, with a `build.sh` and `chapters/`.
- `engine-<hyperscaler>/` for cloud deployment guides (`azure`, `gcp`, `aws`).
- `_shared/` for Typst templates/styles reused across books (to be extracted).
- Adding a new book = adding one new directory — no restructuring needed.
