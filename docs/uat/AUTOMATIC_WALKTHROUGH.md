# Whole-document walkthrough development test

This local test generates a learning edition from a supplied text PDF and learner profiles. It exercises the recommendation and generation pipeline separately from the Android Build 17 sample. The input PDF remains the source of record. The test does not cover OCR, figure interpretation, EPUB conversion, or mobile session integration.

Each profile supplies a background, a learning goal and a presentation preference. The test uses two synthetic profiles for the supplied edition of *Identity Management for Agentic AI*. The product manager wants to explain identity, consent and scope to a team. The IAM engineer wants to design a workflow that spans trust domains, delegates to sub-agents and preserves revocation and audit.

1. Extract all PDF pages with Poppler and retain their one-based file page numbers. Send successive page groups to a local model to identify concepts and supporting passages. Identify each passage during extraction so a model can select it without inventing a page or rewriting a quote.
2. Pass the resulting inventory to the route planner for each profile. Generate four lessons with the reason each belongs in the route. A focused recommendation can draw on the full document without reproducing its chapter order.
3. Generate three passes against the same concept and source anchors. Concepts provides a worked example and ungraded checkpoint. Technical provides assumptions, a mechanism, a failure mode, an exercise and an ungraded checkpoint. Assessment provides two independent multiple-choice questions per concept, including a scenario that tests transfer.
4. Check schema, citation IDs and option uniqueness. A second local model reviews the explanations and independently answers the questions without seeing the proposed key. The browser preview opens any pass directly, preserves quiz drafts while switching within the page, and grades by the saved key after submission. It flags explanations that need review and disables scoring on unconfirmed questions. This is model review and does not certify mastery.

Run from the repository root with Python 3.11+, Poppler (`pdfinfo`, `pdftotext`), and a running local Ollama server on port 11434 with `qwen3.8:27b` and `ministral-3:8b` installed. Supply a fresh output directory to force a new run. An existing directory reuses completed stages for the same model digest and request. Use `--retry-flagged` to attempt one revision of each flagged lesson and review the revision again. Add `--findings` with a JSON map of profile IDs to concept IDs and review notes to request a targeted revision based on manual review.

```sh
python3 scripts/test_pdf_walkthrough.py /path/to/2510.25819v1.pdf \
  tests/fixtures/walkthrough_profiles.json --out output/walkthrough/identity-management

python3 scripts/preview_pdf_walkthrough.py output/walkthrough/identity-management/result.json \
  /path/to/2510.25819v1.pdf --out output/walkthrough/identity-management/walkthrough.html
```

Open the HTML in a desktop browser. Use the learner selector to compare routes, jump directly into Pass 3, select an answer, switch passes and return to submit it. Open a citation to compare the extracted passage against the local PDF. The JSON files and HTML contain local source excerpts and remain under the ignored `output/` directory. The test exits with status 1 for flagged content while preserving the reviewed result for inspection. The preview checks the PDF hash before it renders citation links.

## 2026-11-13 run

- The source SHA-256 was `b019c9420565afe6a3f81fcd8e9b3375f546003cecab93ecfa25d965b2f68bb2`. The inventory processed all 33 PDF pages in nine batches. All 44 evidence snippets matched their extracted source pages. The model also catalogued four bibliography snippets, which neither reviewed route selected.
- The product manager's four lessons covered distinct agent identities, delegation versus impersonation, consent fatigue, and asynchronous approval. The engineer's four covered explicit delegated authority, durable workflows, narrowing sub-agent permissions, and de-provisioning. Their routes drew on 13 and nine PDF pages respectively. Both profiles also completed route planning with the compact claim inventory. The preview retains the first product-manager route, which included excerpts in planning. To replay that route, use `--full-inventory-for beginner` and the saved local stage results. The engineer's original expanded request timed out; the compact planner completed.
- Each route contained four conceptual lessons, four technical treatments, eight ungraded checkpoints, and eight graded questions. A second model independently agreed with all 16 final keys. Manual review found an exercise that compared the agent's capabilities with the delegation grant instead of evaluating the requested action. The targeted regeneration corrected the central distinction and both revised quiz keys were confirmed. Two engineer items still read as general knowledge questions where the prompt requested a new scenario. The review also needs more coverage of distractor quality and scenario transfer before this question bank can be used to assess mastery.
- Chrome checks at desktop and phone widths covered profile switching, opening Pass 3 directly, concept alignment, draft retention across passes, correct and incorrect grading, and citation links. The test exercised local model inference and a browser preview. Android generation, OCR and interpretation of figures remain outside this test.
