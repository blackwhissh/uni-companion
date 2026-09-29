# Functionalities and Use Cases

| Field | Value |
|---|---|
| Document ID | DOC-UC |
| Version | 0.2.1 |
| Status | Draft |
| Last updated | 2026-09-27 |
| Owner | Project owner |

## 1. Purpose

This document lists Uni Companion functionalities and explains each through concrete use cases. It is the product-facing companion to technical architecture documents.

**Material ownership decision (v0.2):** Canonical presentations and PDFs are uploaded by a **course admin / professor**, not by students. Students enroll, study, and match.

**Material visibility decision (v0.2.1):** Course Admin / Professor can **publish** and **unpublish** each uploaded material independently of ingestion status. Students only study materials that are both `READY` and `PUBLISHED`.

## 2. Practical rollout plan

This is the agreed delivery sequence. Product behavior and use-case priorities follow these phases.

```text
Phase A — MVP (now)
  Project owner acts as Course Admin for ONE course (e.g. CISS)
  Admin uploads ONE (or few) official PDFs
  Classmates register → enroll → study (Q&A, cards, quiz) → opt-in matching
  Students do NOT upload course packs

Phase B — v1 (campus validation)
  Real professor / Fachschaft course-admin accounts
  Multi-material courses, verification, richer matching
  Optional: student private notes (never become course truth unless promoted)

Phase C — Later (institutional)
  LMS sync (Moodle / ILIAS) so professors need not re-upload forever
  Multi-course / multi-uni only after Siegen density works
```

| Phase | Who uploads course PDFs? | Who studies? | Matching |
|---|---|---|---|
| **A — MVP** | Project owner as Course Admin | Enrolled students | Opt-in + active count |
| **B — v1** | Professor / Fachschaft admin | Enrolled + verified students | Intents + suggestions + contact |
| **C — Later** | Admin upload **and/or** LMS sync | Broader cohorts | Graph / thesis / agents |

### Phase A operating model (explicit)

| Role in practice | Actor | Does |
|---|---|---|
| You (builder) | A-CADM | Create course, upload PDF(s), publish/unpublish materials, monitor ingestion |
| Classmate | A-STU | Register, enroll, Q&A / cards / quiz, opt into matching |
| System | A-SYS | Ingest, RAG, generate study artifacts, enforce access |

Students never seed the canonical knowledge base in the default path.

---

## 3. Actors

| Actor ID | Actor | Description |
|---|---|---|
| A-STU | Student | Learner at Universität Siegen; studies and matches; does **not** publish course packs |
| A-CADM | Course Admin | Owns a course’s canonical materials (MVP: project owner; later: staff designee) |
| A-PROF | Professor | Course owner; typically holds Course Admin rights (Phase B+) |
| A-PEER | Peer student | Another enrolled student discoverable via matching (opt-in) |
| A-SYS | System | Uni Companion platform (services + AI pipeline) |
| A-FS | Fachschaft / operator | May receive Course Admin for coordination (Phase B+) |

---

## 4. Conventions

| Field | Meaning |
|---|---|
| **Functionality** | Capability the product provides |
| **Use case ID** | `UC-###` (stable) |
| **Priority** | Must / Should / Could / Won't (near term) |
| **Phase** | MVP (A) · v1 (B) · Future (C+) |

Use case format: Goal · Preconditions · Main flow · Exceptions · Postconditions.

---

## 5. Functionality map

| Area | Functionalities |
|---|---|
| **F1 Identity** | Register, login, profile, roles (student / course admin) |
| **F2 Courses & materials** | Admin creates course, uploads PDFs, publish/unpublish per material; students enroll and consume |
| **F3 Study AI** | Q&A (RAG), flashcards, quiz, search, summaries, bilingual explain |
| **F4 Progress** | Results history, weak topics, study plan |
| **F5 Matching** | Opt-in, active count, intents, suggestions, contact request |
| **F6 Trust & privacy** | Enrolled-only material access, visibility controls, uni-email verification |

```text
A-CADM / A-PROF
    │  create course → upload PDFs → publish/unpublish each material
    ▼
F2 Canonical course knowledge base (only PUBLISHED + READY visible to students)
    │
    ├──────────────────► F3 Study AI ◄── A-STU (enrolled)
    │                         │
    │                         ▼
    │                    F4 Progress
    │                         │
    └──────────────────► F5 Matching
                              │
                       F6 Trust (enrolled-only materials)
```

---

## 6. F1 — Identity

### F1.1 Register and authenticate

**Description:** Users create accounts. Most users are students. Course Admin is assigned (seeded) for MVP; self-serve professor onboarding comes in Phase B.

#### UC-001 — Register a new student account

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Create a student account |
| **Preconditions** | Email not already registered |
| **Main flow** | 1. Student opens Register.<br>2. Enters email, password, display name.<br>3. System creates account with role `STUDENT`.<br>4. System signs the student in (or prompts login).<br>5. System shows home / course list. |
| **Exceptions** | E1: Email already used → offer login.<br>E2: Weak password → reject with rules. |
| **Postconditions** | Student account exists. |

#### UC-002 — Log in

| Field | Content |
|---|---|
| **Actors** | A-STU or A-CADM, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Access an existing account |
| **Preconditions** | Account exists |
| **Main flow** | 1. User enters credentials.<br>2. System authenticates.<br>3. System routes by role: student → courses; course admin → admin course console (+ student views if dual-role). |
| **Exceptions** | E1: Invalid credentials → generic failure.<br>E2: Locked/unverified (later) → guided next step. |
| **Postconditions** | Authenticated session established. |

#### UC-005 — Bootstrap Course Admin (MVP)

| Field | Content |
|---|---|
| **Actors** | A-CADM, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Ensure the project owner can manage the pilot course |
| **Preconditions** | Deployment/seed access available |
| **Main flow** | 1. Operator creates/seeds an account with role `COURSE_ADMIN` (and optionally `STUDENT`).<br>2. Admin can create courses and upload materials.<br>3. No public “become admin” self-service in MVP. |
| **Postconditions** | At least one Course Admin exists for the pilot. |

---

### F1.2 Profile and verification

#### UC-003 — Update profile

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Keep display name / interests up to date |
| **Preconditions** | Authenticated |
| **Main flow** | 1. Open Profile → edit name/interests → save. |
| **Postconditions** | Profile updated. |

#### UC-004 — Verify university email *(v1)*

| Field | Content |
|---|---|
| **Actors** | A-STU (or A-PROF), A-SYS |
| **Priority / Phase** | Should · v1 |
| **Goal** | Prove Uni Siegen affiliation for trusted matching / admin eligibility |
| **Preconditions** | Authenticated; university-domain email |
| **Main flow** | 1. Request verification → confirm link/code → mark verified. |
| **Postconditions** | Verification flag set. |

---

## 7. F2 — Courses and materials (admin-owned)

### F2.1 Course lifecycle (Course Admin)

**Description:** Canonical course content is owned by Course Admin / Professor. Students only enroll and consume published materials.

#### UC-010 — Create / open course (admin)

| Field | Content |
|---|---|
| **Actors** | A-CADM (MVP) / A-PROF (v1), A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Establish a course container (e.g. CISS – Distributed Systems) |
| **Preconditions** | User has `COURSE_ADMIN` (or professor) rights |
| **Main flow** | 1. Admin creates course (title, code, term).<br>2. System stores course as unpublished or published shell.<br>3. Admin opens course admin console (materials, enrollments, status). |
| **Postconditions** | Course exists and is manageable by admin. |

#### UC-014 — Publish / unpublish course *(MVP minimal publish)*

| Field | Content |
|---|---|
| **Actors** | A-CADM, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Make the course visible for student enrollment |
| **Preconditions** | Course created |
| **Main flow** | 1. Admin sets course visibility to published.<br>2. Students can find and enroll.<br>3. Admin may unpublish later (hides new enrollments; policy for existing TBD). |
| **Postconditions** | Course discoverable by students when published. |

#### UC-011 — Enroll in a course (student)

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Join the course cohort to access published materials and matching |
| **Preconditions** | Course published; student authenticated |
| **Main flow** | 1. Student finds/opens the course.<br>2. Student chooses Enroll.<br>3. System records enrollment.<br>4. Student gains access to published `READY` materials and study tools.<br>5. Active count may update. |
| **Exceptions** | E1: Course unpublished → cannot enroll.<br>E2: Already enrolled → open course home. |
| **Postconditions** | Enrollment stored; material access granted per policy. |

#### UC-015 — Open enrolled course (student)

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Enter study context |
| **Preconditions** | Student enrolled |
| **Main flow** | 1. Student selects course.<br>2. System shows course home: material list (published), Q&A, cards, quiz, matching opt-in, active count. |
| **Postconditions** | Active course context set. |

---

### F2.2 Canonical material upload (Course Admin only)

**Description:** Presentations/PDFs that define the course knowledge base are uploaded by Course Admin / Professor. Default student upload of course packs is **out of scope**.

#### UC-012 — Upload lecture PDF (admin)

| Field | Content |
|---|---|
| **Actors** | A-CADM / A-PROF, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Add official course material into the ingestion pipeline |
| **Preconditions** | Course exists; actor is Course Admin for that course; file is PDF within size limits |
| **Main flow** | 1. Admin opens course materials.<br>2. Admin uploads PDF (and optional title/lecture number).<br>3. System stores file as course-scoped canonical material with visibility `UNPUBLISHED` by default.<br>4. Status → `PROCESSING`; ingestion runs (parse → chunk → embed).<br>5. On success, processing status → `READY`.<br>6. Admin sees status in admin console. Students do **not** study it until UC-018 publish. |
| **Exceptions** | E1: Unsupported type/size → reject.<br>E2: Ingestion fails → `FAILED`; admin can retry/replace.<br>E3: Non-admin attempts upload → forbidden. |
| **Postconditions** | Canonical material exists; may be `READY` but still unpublished. |

#### UC-013 — Monitor material readiness

| Field | Content |
|---|---|
| **Actors** | A-CADM (primary), A-STU (read-only where visible), A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Know when processing finished |
| **Preconditions** | Upload accepted |
| **Main flow** | 1. Admin views processing status: `UPLOADED` → `PROCESSING` → `READY` / `FAILED`.<br>2. Visibility remains independent (`UNPUBLISHED` / `PUBLISHED`). |
| **Postconditions** | Admin knows whether publish will expose usable content. |

#### UC-018 — Publish / unpublish material (admin)

| Field | Content |
|---|---|
| **Actors** | A-CADM / A-PROF, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Control whether enrolled students can see and study a material |
| **Preconditions** | Material exists; actor is Course Admin for that course |
| **Main flow** | 1. Admin selects a material.<br>2. Admin sets visibility to `PUBLISHED` or `UNPUBLISHED`.<br>3. If `PUBLISHED` and `READY`, material appears in student lists and is included in RAG/cards/quiz retrieval.<br>4. If `UNPUBLISHED`, material is hidden from students and excluded from retrieval immediately (vectors may remain stored). |
| **Exceptions** | E1: Publish while not `READY` → allow flag but student study stays blocked until `READY` (or block publish until ready — product choice; default: allow flag, gate on both).<br>E2: Non-admin → forbidden. |
| **Postconditions** | Visibility updated; student access follows `READY` ∧ `PUBLISHED` ∧ enrolled. |

#### UC-016 — Replace material *(v1)*

| Field | Content |
|---|---|
| **Actors** | A-CADM / A-PROF, A-SYS |
| **Priority / Phase** | Should · v1 |
| **Goal** | Correct slides without leaving stale knowledge live |
| **Main flow** | 1. Admin replaces a PDF (optionally unpublish first via UC-018).<br>2. System re-ingests replacement and updates/hides old chunks per policy. |
| **Postconditions** | Students see/study the intended version when published + ready. |

#### UC-017 — Student private notes upload *(optional later)*

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Could · Future (not default path) |
| **Goal** | Let a student add personal notes for private study only |
| **Preconditions** | Enrolled |
| **Main flow** | 1. Student uploads a personal note PDF.<br>2. System keeps it **private to that student**.<br>3. It does **not** enter the shared course knowledge base unless a Course Admin explicitly promotes it (separate future UC). |
| **Postconditions** | Private artifact only; no cohort redistribution. |

---

## 8. F3 — Study AI

Study features run only against **canonical published materials** the student is allowed to access (enrollment).

### F3.1 Grounded Q&A (RAG)

#### UC-020 — Ask a question about the course

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Understand a topic using official lecture material |
| **Preconditions** | Enrolled; ≥1 material `READY` and `PUBLISHED` |
| **Main flow** | 1. Open Q&A.<br>2. Ask question.<br>3. System retrieves course chunks.<br>4. Returns grounded answer + citations when possible.<br>5. Optional follow-ups. |
| **Exceptions** | E1: Not enrolled → deny.<br>E2: No relevant chunks → say material may not cover it.<br>E3: Provider error → retry. |
| **Postconditions** | Answer delivered from canonical KB. |

### F3.2 Flashcards

#### UC-021 — Generate and review flashcards

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Practice key concepts from official PDFs |
| **Preconditions** | Enrolled; material `READY` and `PUBLISHED` |
| **Main flow** | Generate deck → review cards → optional known/unknown marks. |
| **Postconditions** | Deck available for the user/course. |

### F3.3 Quiz

#### UC-022 — Take a generated quiz

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Must · MVP |
| **Goal** | Self-check before exams |
| **Preconditions** | Enrolled; material `READY` and `PUBLISHED` |
| **Main flow** | Generate quiz → answer → score → store attempt. |
| **Postconditions** | Attempt saved. |

### F3.4–F3.6 v1 study enhancements

| UC | Title | Phase | Notes |
|---|---|---|---|
| UC-023 | Semantic search across lectures | Should · v1 | Enrolled-only |
| UC-024 | Summarize a lecture PDF | Should · v1 | From canonical material |
| UC-025 | Explain in DE or EN | Should · v1 | Still grounded |

---

## 9. F4 — Progress and personalization

#### UC-030 — View study progress

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Should · v1 (persist attempts in MVP via UC-022) |
| **Goal** | See scores / weak topics |
| **Main flow** | Open Progress → review attempts → jump to cards/Q&A for gaps. |

#### UC-031 — Create a simple study plan

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Priority / Phase** | Could · v1; agents in Future |
| **Goal** | Plan toward an exam date using official materials + gaps |

---

## 10. F5 — Matching and collaboration

Matching is among **enrolled** students; it does not depend on who uploaded PDFs.

#### UC-040 — Opt in to finding a study partner — Must · MVP

Student enables discoverability for the course.

#### UC-041 — See active student count — Must · MVP

Real enrolled/active count only; never fabricated.

#### UC-042 — Set collaboration intents — Should · v1

Study group / project / thesis / language.

#### UC-043 — Get partner suggestions — Should · v1

Same course + compatible intent (graph later).

#### UC-044 — Request contact / connect — Should · v1

Share contact only on accept.

#### UC-045 — Complementary skill / thesis matching — Could · Future

---

## 11. F6 — Trust and privacy

### Access policy (decided)

| Resource | Who can access |
|---|---|
| Canonical course PDFs / chunks / grounded answers | **Course Admin** (manage) + **enrolled students** (study) |
| Student private notes (if ever enabled) | Owning student only |
| Matching profile | Opt-in peers only |

#### UC-050 — Enforce enrolled-only material access — Must · MVP

| Field | Content |
|---|---|
| **Actors** | A-STU, A-SYS |
| **Goal** | Prevent unauthorized access to lecture materials |
| **Main flow** | On material/Q&A/card/quiz access, system checks enrollment (or admin role). Deny otherwise. |
| **Postconditions** | No cross-course or public leakage of canonical PDFs. |

#### UC-051 — Control matching visibility — Should · v1

Opt-in discoverability and field visibility.

#### UC-052 — Professor / Fachschaft admin onboarding — Should · v1

| Field | Content |
|---|---|
| **Actors** | A-PROF / A-FS, A-SYS |
| **Priority / Phase** | Should · v1 |
| **Goal** | Hand course ownership from project owner to real staff |
| **Main flow** | 1. Verified university user is granted `COURSE_ADMIN` for a course.<br>2. They take over UC-010/UC-012.<br>3. Project owner may retain operator access. |
| **Postconditions** | Official materials managed by institution-side admin. |

#### UC-060 — Sync materials from LMS — Could · Future (Phase C)

| Field | Content |
|---|---|
| **Actors** | A-PROF, A-SYS, external LMS |
| **Goal** | Reduce manual re-upload of slides |
| **Main flow** | With permission, system imports/links course files from Moodle/ILIAS into canonical materials and runs ingestion. |
| **Postconditions** | Canonical KB stays aligned with LMS without student uploads. |

---

## 12. End-to-end scenarios

### Scenario S-0 — Admin prep (Phase A, before classmates arrive)

1. UC-005 — Bootstrap Course Admin (you)  
2. UC-002 — Log in as admin  
3. UC-010 — Create CISS – Distributed Systems  
4. UC-014 — Publish course  
5. UC-012 + UC-013 — Upload PDF → `READY`  
6. UC-018 — Publish material  

### Scenario S-1 — Student MVP demo (≤ 5 minutes)

1. UC-001 / UC-002 — Register or log in (student)  
2. UC-011 + UC-015 — Enroll and open course  
3. UC-020 — Ask a grounded question  
4. UC-021 — Review flashcards  
5. UC-022 — Take a short quiz  
6. UC-040 + UC-041 — Opt in; see active count  

*(Students do not upload the course PDF in this demo.)*

### Scenario S-2 — Exam week (Phase B / v1)

1. UC-023 / UC-025 — Search + DE/EN explain  
2. UC-022 → UC-030 — Quiz + weak topics  
3. UC-031 — Study plan (optional)  
4. UC-042 → UC-043 → UC-044 — Form a study group  

### Scenario S-3 — Hand off to professor (Phase B)

1. UC-004 — Verify uni email  
2. UC-052 — Grant Course Admin to professor/Fachschaft  
3. UC-012 / UC-016 — Professor maintains materials  

### Scenario S-4 — LMS-assisted course (Phase C)

1. UC-060 — Sync from Moodle/ILIAS  
2. Students continue S-1/S-2 flows unchanged  

### Scenario S-5 — Thesis partner (Future)

1. UC-003 + UC-042 (thesis) → UC-045 → UC-044  

---

## 13. Priority backlog (by rollout phase)

### Phase A — MVP (Must)

| UC | Summary | Actor |
|---|---|---|
| UC-005 | Bootstrap Course Admin | A-CADM |
| UC-001, UC-002, UC-003 | Student identity/profile | A-STU |
| UC-010, UC-014 | Create + publish course | A-CADM |
| UC-012, UC-013, UC-018 | Upload PDF + readiness + publish/unpublish material | A-CADM |
| UC-011, UC-015 | Enroll + open course | A-STU |
| UC-020, UC-021, UC-022 | Q&A, cards, quiz | A-STU |
| UC-040, UC-041 | Matching opt-in + count | A-STU |
| UC-050 | Enrolled-only access | A-SYS |

### Phase B — v1 (Should)

UC-004, UC-016, UC-023, UC-024, UC-025, UC-030, UC-042, UC-043, UC-044, UC-051, UC-052
*(Material publish/unpublish is UC-018 in Phase A, not UC-016.)*

### Phase C+ — Future (Could)

UC-017 (private notes), UC-031 (plans/agents), UC-045 (graph matching), UC-060 (LMS sync)

---

## 14. Out of scope (Won't — near term)

| Idea | Why excluded |
|---|---|
| Students uploading canonical course PDFs by default | Copyright, quality, trust; conflicts with rollout |
| Public marketplace of lecture PDFs | Legal / trust risk |
| Generic ungrounded chatbot positioning | Commodity |
| Video study rooms | Premature |
| Full LMS replacement | Different product; sync ≠ replace |

---

## 15. Traceability (product → system)

| Use cases | Area | Typical services (target) |
|---|---|---|
| UC-001–005, UC-052 | Identity / roles | identity-service |
| UC-010–017, UC-060 | Courses & canonical materials | course-service, ingestion-service |
| UC-020–025 | Study AI | rag-service, study-service |
| UC-030–031 | Progress | study-service |
| UC-040–045 | Matching | matching-service |
| UC-050–051 | Trust | gateway + owning services |

---

## 16. Open product questions

| ID | Question | Status |
|---|---|---|
| Q-1 | Primary “aha”: Q&A, quiz, or matching? | Open |
| Q-2 | After match accept: uni-email only or WhatsApp link too? | Open |
| Q-3 | Material access model | **Decided:** canonical materials = admin-uploaded; visible to enrolled students |
| Q-4 | UI language first: EN, DE, or both? | Open |
| Q-5 | MVP enrollment: open enroll vs invite code? | Open (invite code reduces random access) |

---

## 17. Document history

| Version | Date | Notes |
|---|---|---|
| 0.1.0 | 2026-09-27 | Initial functionalities and use cases (student upload) |
| 0.2.0 | 2026-09-27 | Admin/professor-owned materials; practical rollout Phases A–C; enrollment-gated access; student upload removed from default path |
| 0.2.1 | 2026-09-27 | Material publish/unpublish decision; UC-018 Must MVP; upload default unpublished |
