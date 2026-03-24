// ── Language data ─────────────────────────────────────────────────────────────
// All supported Minecraft language codes — drives the custom dropdown.

const LANGUAGES = [
    { code: "af_za",    name: "Afrikaans (Suid-Afrika)" },
    { code: "ar_sa",    name: "اللغة العربية — Arabic" },
    { code: "ast_es",   name: "Asturianu — Asturian" },
    { code: "az_az",    name: "Azərbaycanca — Azerbaijani" },
    { code: "ba_ru",    name: "Башҡортса — Bashkir" },
    { code: "bar",      name: "Boarisch — Bavarian" },
    { code: "be_by",    name: "Беларуская — Belarusian" },
    { code: "bg_bg",    name: "Български — Bulgarian" },
    { code: "br_fr",    name: "Brezhoneg — Breton" },
    { code: "brb",      name: "Braobans — Brabantian" },
    { code: "bs_ba",    name: "Bosanski — Bosnian" },
    { code: "ca_es",    name: "Català — Catalan" },
    { code: "cs_cz",    name: "Čeština — Czech" },
    { code: "cy_gb",    name: "Cymraeg — Welsh" },
    { code: "da_dk",    name: "Dansk — Danish" },
    { code: "de_at",    name: "Österreichisches Deitsch — Austrian German" },
    { code: "de_ch",    name: "Schwiizerdutsch — Swiss German" },
    { code: "de_de",    name: "Deutsch — German" },
    { code: "el_gr",    name: "Ελληνικά — Greek" },
    { code: "en_au",    name: "English (Australia)" },
    { code: "en_ca",    name: "English (Canada)" },
    { code: "en_gb",    name: "English (United Kingdom)" },
    { code: "en_nz",    name: "English (New Zealand)" },
    { code: "en_pt",    name: "Pirate Speak" },
    { code: "en_ud",    name: "ɥsᴉꞁᵷuƎ — Upside Down English" },
    { code: "enp",      name: "Anglish" },
    { code: "enws",     name: "Shakespearean English" },
    { code: "eo_uy",    name: "Esperanto" },
    { code: "es_ar",    name: "Español (Argentina)" },
    { code: "es_cl",    name: "Español (Chile)" },
    { code: "es_ec",    name: "Español (Ecuador)" },
    { code: "es_es",    name: "Español (España)" },
    { code: "es_mx",    name: "Español (México)" },
    { code: "es_uy",    name: "Español (Uruguay)" },
    { code: "es_ve",    name: "Español (Venezuela)" },
    { code: "esan",     name: "Andalûh — Andalusian" },
    { code: "et_ee",    name: "Eesti — Estonian" },
    { code: "eu_es",    name: "Euskara — Basque" },
    { code: "fa_ir",    name: "فارسی — Persian" },
    { code: "fi_fi",    name: "Suomi — Finnish" },
    { code: "fil_ph",   name: "Filipino" },
    { code: "fo_fo",    name: "Føroyskt — Faroese" },
    { code: "fr_ca",    name: "Français (Québec)" },
    { code: "fr_fr",    name: "Français (France)" },
    { code: "fra_de",   name: "Fränggisch — East Franconian" },
    { code: "fur_it",   name: "Furlan — Friulian" },
    { code: "fy_nl",    name: "Frysk — Frisian" },
    { code: "ga_ie",    name: "Gaeilge — Irish" },
    { code: "gd_gb",    name: "Gàidhlig — Scottish Gaelic" },
    { code: "gl_es",    name: "Galego — Galician" },
    { code: "haw_us",   name: "ʻŌlelo Hawaiʻi — Hawaiian" },
    { code: "he_il",    name: "עברית — Hebrew" },
    { code: "hi_in",    name: "हिन्दी — Hindi" },
    { code: "hr_hr",    name: "Hrvatski — Croatian" },
    { code: "hu_hu",    name: "Magyar — Hungarian" },
    { code: "hy_am",    name: "Հայերեն — Armenian" },
    { code: "id_id",    name: "Bahasa Indonesia" },
    { code: "ig_ng",    name: "Igbo" },
    { code: "io_en",    name: "Ido" },
    { code: "is_is",    name: "Íslenska — Icelandic" },
    { code: "isv",      name: "Medžuslovjansky — Interslavic" },
    { code: "it_it",    name: "Italiano (Italia)" },
    { code: "ja_jp",    name: "日本語 — Japanese" },
    { code: "jbo_en",   name: "la .lojban. — Lojban" },
    { code: "ka_ge",    name: "ქართული — Georgian" },
    { code: "kk_kz",    name: "Қазақша — Kazakh" },
    { code: "kn_in",    name: "ಕನ್ನಡ — Kannada" },
    { code: "ko_kr",    name: "한국어 — Korean" },
    { code: "ksh",      name: "Kölsch/Ripoarisch" },
    { code: "kw_gb",    name: "Kernewek — Cornish" },
    { code: "la_la",    name: "Latina — Latin" },
    { code: "lb_lu",    name: "Lëtzebuergesch — Luxembourgish" },
    { code: "li_li",    name: "Limburgs — Limburgish" },
    { code: "lmo",      name: "Lombard" },
    { code: "lo_la",    name: "ລາວ — Lao" },
    { code: "lol_us",   name: "LOLCAT" },
    { code: "lt_lt",    name: "Lietuvių — Lithuanian" },
    { code: "lv_lv",    name: "Latviešu — Latvian" },
    { code: "lzh",      name: "文言 — Classical Chinese" },
    { code: "mk_mk",    name: "Македонски — Macedonian" },
    { code: "mn_mn",    name: "Монгол — Mongolian" },
    { code: "ms_my",    name: "Bahasa Melayu — Malay" },
    { code: "mt_mt",    name: "Malti — Maltese" },
    { code: "nah",      name: "Mēxikatlahtōlli — Nahuatl" },
    { code: "nds_de",   name: "Platdüütsk — Low German" },
    { code: "nl_be",    name: "Vlaams — Flemish Dutch" },
    { code: "nl_nl",    name: "Nederlands — Dutch" },
    { code: "nn_no",    name: "Norsk nynorsk" },
    { code: "nb_no",    name: "Norsk Bokmål" },
    { code: "oc_fr",    name: "Occitan" },
    { code: "ovd",      name: "Övdalsk — Elfdalian" },
    { code: "pl_pl",    name: "Polski — Polish" },
    { code: "pt_br",    name: "Português (Brasil)" },
    { code: "pt_pt",    name: "Português (Portugal)" },
    { code: "qya_aa",   name: "Quenya (Elvish)" },
    { code: "ro_ro",    name: "Română — Romanian" },
    { code: "rpr",      name: "Дореформенный русскiй — Russian (Pre-revolutionary)" },
    { code: "ru_ru",    name: "Русский — Russian" },
    { code: "ry_ua",    name: "Руснацькый — Rusyn" },
    { code: "sah_sah",  name: "Сахалыы — Yakut" },
    { code: "se_no",    name: "Davvisámegiella — Northern Sami" },
    { code: "sk_sk",    name: "Slovenčina — Slovak" },
    { code: "sl_si",    name: "Slovenščina — Slovenian" },
    { code: "so_so",    name: "Af-Soomaali — Somali" },
    { code: "sq_al",    name: "Shqip — Albanian" },
    { code: "sr_cs",    name: "Srpski — Serbian (Latin)" },
    { code: "sr_sp",    name: "Српски — Serbian (Cyrillic)" },
    { code: "sv_se",    name: "Svenska — Swedish" },
    { code: "sxu",      name: "Säggs'sch — Upper Saxon German" },
    { code: "szl",      name: "Ślōnskŏ — Silesian" },
    { code: "ta_in",    name: "தமிழ் — Tamil" },
    { code: "th_th",    name: "ไทย — Thai" },
    { code: "tl_ph",    name: "Tagalog" },
    { code: "tlh_aa",   name: "tlhIngan Hol — Klingon" },
    { code: "tok",      name: "toki pona" },
    { code: "tr_tr",    name: "Türkçe — Turkish" },
    { code: "tt_ru",    name: "Татарча — Tatar" },
    { code: "uk_ua",    name: "Українська — Ukrainian" },
    { code: "val_es",   name: "Català (Valencià) — Valencian" },
    { code: "vec_it",   name: "Vèneto — Venetian" },
    { code: "vi_vn",    name: "Tiếng Việt — Vietnamese" },
    { code: "yi_de",    name: "ייִדיש — Yiddish" },
    { code: "yo_ng",    name: "Yorùbá" },
    { code: "zh_cn",    name: "简体中文 — Chinese Simplified" },
    { code: "zh_hk",    name: "繁體中文（香港）— Chinese Traditional (HK)" },
    { code: "zh_tw",    name: "繁體中文（台灣）— Chinese Traditional (TW)" },
    { code: "zlm_arab", name: "بهاس ملايو — Malay (Jawi)" },
];

// Local persistence

function storageKey(langCode) { return `ch_trans_${langCode}`; }

function saveToStorage(langCode, data) {
    try { localStorage.setItem(storageKey(langCode), JSON.stringify(data)); } catch (_) {}
}

function loadFromStorage(langCode) {
    try { return JSON.parse(localStorage.getItem(storageKey(langCode))); } catch (_) { return null; }
}

function clearStorage(langCode) {
    try { localStorage.removeItem(storageKey(langCode)); } catch (_) {}
}

// ── Constants ─────────────────────────────────────────────────────────────────

const RAW_BASE = "https://raw.githubusercontent.com/";

const REFERENCE_URL =
    RAW_BASE +
    "bitmochibit/createharmonics/refs/heads/master/forge/src/generated/resources/assets/createharmonics/lang/en_us.json";

function getLangUrl(langCode) {
    return (
        RAW_BASE +
        `bitmochibit/createharmonics/refs/heads/master/common/src/main/resources/assets/createharmonics/lang/${langCode}.json`
    );
}

const PAGE_SIZE = 50;

// ── State ─────────────────────────────────────────────────────────────────────

let referenceData = null;
let existingData = {};
let allKeys = [];
let currentPage = 1;
// Persists edited values across page changes: key → string (raw, with real \n)
const editedValues = {};

// Dropdown selection
let selectedLangCode = null;

// ── Bootstrap ─────────────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", () => {
    lucide.createIcons()
    setupDisc();
    setupDropdown();
    setupControls();
});

// ── Disc animation ────────────────────────────────────────────────────────────

function setupDisc() {
    const recordTextureNames = [
        "brass", "creative", "diamond", "emerald",
        "gold", "netherite", "stone",
    ];

    const harmonicsColorVariations = [
        "#FFCD67",
        "#FF89D0",
        "#6DF5E9",
        "#65EC94",
        "#FFEA7F",
        "#A38C52",
        "#D4D4D4"
    ]


    const randomIndex = Math.floor(Math.random() * recordTextureNames.length);

    const placeholder = document.querySelector(".placeholder");
    const randomDisc = document.querySelector(".mod-disc.random");

    gsap.set(randomDisc, { autoAlpha: 0, display: "block" });

    randomDisc.src =
        `https://github.com/bitmochibit/createharmonics/blob/master/common/src/main/resources/assets/createharmonics/textures/block/ethereal_record_visual/${recordTextureNames[randomIndex]}.png?raw=true`;

    randomDisc.onload = () => {
        gsap.timeline()
            .to(placeholder, { autoAlpha: 0, duration: 0.4, ease: "power2.in" })
            .to(randomDisc,  { autoAlpha: 1, duration: 0.6, ease: "power2.out" }, "-=0.1")
            .to(".disc-colored-text", {
                duration: 1.5,
                color: harmonicsColorVariations[randomIndex],
                ease: "power2.inOut",
            });
    };
}

// ── Custom dropdown ───────────────────────────────────────────────────────────

function setupDropdown() {
    const wrapper  = document.getElementById("c-select");
    const trigger  = document.getElementById("c-select__trigger");
    const panel    = document.getElementById("c-select__panel");
    const search   = document.getElementById("c-select__search");
    const list     = document.getElementById("c-select__list");
    const label    = document.getElementById("c-select__label");

    function renderOptions(filter = "") {
        list.innerHTML = "";
        const q = filter.trim().toLowerCase();
        const filtered = q
            ? LANGUAGES.filter(l =>
                l.name.toLowerCase().includes(q) || l.code.toLowerCase().includes(q))
            : LANGUAGES;

        if (filtered.length === 0) {
            const li = document.createElement("li");
            li.className = "c-select__option c-select__option--empty";
            li.textContent = "No languages match your search";
            list.appendChild(li);
            return;
        }

        for (const lang of filtered) {
            const li = document.createElement("li");
            li.className = "c-select__option";
            li.textContent = lang.name;
            li.dataset.code = lang.code;
            li.setAttribute("role", "option");
            if (lang.code === selectedLangCode) {
                li.setAttribute("aria-selected", "true");
            }
            li.addEventListener("click", () => selectLang(lang.code, lang.name));
            list.appendChild(li);
        }
    }

    function openPanel() {
        panel.hidden = false;
        trigger.setAttribute("aria-expanded", "true");
        search.value = "";
        renderOptions();
        search.focus();
    }

    function closePanel() {
        panel.hidden = true;
        trigger.setAttribute("aria-expanded", "false");
    }

    function selectLang(code, name) {
        selectedLangCode = code;
        label.textContent = name;
        localStorage.setItem("ch_last_lang", code);
        closePanel();
    }

    trigger.addEventListener("click", () => {
        panel.hidden ? openPanel() : closePanel();
    });

    trigger.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") { e.preventDefault(); openPanel(); }
        if (e.key === "Escape") closePanel();
    });

    search.addEventListener("input", () => renderOptions(search.value));
    search.addEventListener("keydown", (e) => {
        if (e.key === "Escape") closePanel();
    });

    // Close on outside click
    document.addEventListener("click", (e) => {
        if (!wrapper.contains(e.target)) closePanel();
    });
}

// ── Controls ──────────────────────────────────────────────────────────────────

function setupControls() {
    document.getElementById("load-btn").addEventListener("click", () => {
        if (!selectedLangCode) {
            setStatus("Please select a language first.", "err");
            return;
        }
        loadTranslations(selectedLangCode);
    });

    document.getElementById("clear-btn").addEventListener("click", () => {
        Swal.fire({
            title: "Are you sure?",
            text: "This will delete ALL of your in-browser translations!",
            icon: "warning",
            showCancelButton: true,
            confirmButtonColor: "#3085d6",
            cancelButtonColor: "#d33",
            confirmButtonText: "Yes, delete it!"
        }).then((result) => {
            if (result.isConfirmed) {
                Swal.fire({
                    title: "Deleted!",
                    text: "Your file has been deleted.",
                    icon: "success"
                });
                if (!selectedLangCode) { setStatus("Please select a language first.", "err"); return; }
                clearStorage(selectedLangCode);
                loadTranslations(selectedLangCode);
                setStatus("Translation progress cleared!", "warn");
            }
        });
    });

    document.getElementById("download-btn").addEventListener("click", downloadTranslation);

    const lastLang = localStorage.getItem("ch_last_lang");
    if (lastLang) {
        const lang = LANGUAGES.find(l => l.code === lastLang);
        if (lang) {
            document.getElementById("c-select__label").textContent = lang.name;
            selectedLangCode = lastLang;
            loadTranslations(lastLang);
        }
    }
}

// ── Load ──────────────────────────────────────────────────────────────────────

async function loadTranslations(langCode) {
    const loadBtn    = document.getElementById("load-btn");
    const downloadBtn = document.getElementById("download-btn");
    const entryContainer = document.getElementById("translation-entry");

    // Reset state
    entryContainer.innerHTML = "";
    removePagination();
    downloadBtn.disabled = true;
    currentPage = 1;
    allKeys = [];
    Object.keys(editedValues).forEach(k => delete editedValues[k]);

    setStatus("Fetching reference…", "info");
    loadBtn.disabled = true;

    try {
        const refResponse = await fetch(REFERENCE_URL);
        if (!refResponse.ok) throw new Error("Could not load en_us reference file.");
        referenceData = await refResponse.json();
        allKeys = Object.keys(referenceData);

        existingData = {};
        setStatus(`Fetching existing ${langCode} translations…`, "info");

        try {
            const langResponse = await fetch(getLangUrl(langCode));
            if (langResponse.ok) {
                existingData = await langResponse.json();
                const count = Object.keys(existingData).length;
                setStatus(
                    `Loaded ${count} existing entries for <strong>${langCode}</strong> — ${allKeys.length - count} keys still missing.`,
                    "ok"
                );
            } else {
                setStatus(
                    `No existing file found for <strong>${langCode}</strong>. Starting fresh.`,
                    "warn"
                );
            }
        } catch {
            setStatus(`Could not fetch ${langCode} file — starting fresh.`, "warn");
        }

        // Pre-fill editedValues from existing translations.
        // JSON.parse already converts \n escape sequences to real newline characters,
        // so textareas will display them correctly as line-breaks.
        for (const key of allKeys) {
            editedValues[key] = existingData[key] ?? "";
        }

        // Restore from storage if present old modifies
        const saved = loadFromStorage(langCode);
        if (saved) {
            for (const key of allKeys) {
                if (saved[key] !== undefined) editedValues[key] = saved[key];
            }
        }

        downloadBtn.disabled = false;
        renderPage(1);

    } catch (err) {
        setStatus(`Error: ${err.message}`, "err");
    } finally {
        loadBtn.disabled = false;
    }
}

// ── Textarea auto-resize ──────────────────────────────────────────────────────

function autoResize(ta) {
    ta.style.height = "auto";
    ta.style.height = ta.scrollHeight + "px";
}

// ── Pagination ────────────────────────────────────────────────────────────────

function totalPages() {
    return Math.ceil(allKeys.length / PAGE_SIZE);
}

/** Flush current page's textarea values into editedValues before navigating. */
function saveCurrentPage() {
    document.querySelectorAll(".translation-row").forEach(row => {
        editedValues[row.dataset.key] = row.querySelector(".col-translation").value;
    });
}

function renderPage(page) {
    saveCurrentPage();
    currentPage = page;

    const container = document.getElementById("translation-entry");
    container.innerHTML = "";

    gsap.set(container, { autoAlpha: 0, display: "block" });

    const start = (page - 1) * PAGE_SIZE;
    const end   = Math.min(start + PAGE_SIZE, allKeys.length);
    const pageKeys = allKeys.slice(start, end);

    const fragment = document.createDocumentFragment();

    for (const key of pageKeys) {
        const refValue   = referenceData[key] ?? "";
        const transValue = editedValues[key] ?? "";

        const row = document.createElement("div");
        row.className = "translation-row";
        row.dataset.key = key;

        // Key label
        const keyLabel = document.createElement("span");
        keyLabel.className = "col-key";
        keyLabel.textContent = key;
        keyLabel.title = key;

        // Reference textarea (read-only)
        const refArea = document.createElement("textarea");
        refArea.className = "col-reference";
        refArea.value = refValue;          // real \n characters render as line-breaks
        refArea.readOnly = true;
        refArea.tabIndex = -1;
        refArea.rows = 1;

        // Translation textarea (editable)
        const transArea = document.createElement("textarea");
        transArea.className = "col-translation";
        transArea.value = transValue;
        transArea.placeholder = "Enter translation…";
        transArea.rows = 1;

        if (!transValue.trim()) row.classList.add("missing");

        transArea.addEventListener("input", () => {
            editedValues[key] = transArea.value;
            saveToStorage(selectedLangCode, editedValues);
            row.classList.toggle("missing", transArea.value.trim() === "");
            autoResize(transArea);
            // Keep reference area in sync height-wise
            autoResize(refArea);
        });

        row.appendChild(keyLabel);
        row.appendChild(refArea);
        row.appendChild(transArea);
        fragment.appendChild(row);
    }

    container.appendChild(fragment);

    // Auto-resize all textareas after they are in the DOM
    container.querySelectorAll("textarea").forEach(autoResize);

    renderPagination();

    gsap.timeline()
        .to(".table-placeholder", { autoAlpha: 0, duration: 0.5, ease: "power2.in" })
        .to(container,  { autoAlpha: 1, duration: 0.6, ease: "power2.out" }, "-=0.1");
}

// ── Pagination rendering ──────────────────────────────────────────────────────

function renderPagination() {
    removePagination();

    const total = totalPages();
    if (total <= 1) return;

    const nav = document.createElement("div");
    nav.className = "pagination";
    nav.id = "pagination";

    const start = (currentPage - 1) * PAGE_SIZE + 1;
    const end   = Math.min(currentPage * PAGE_SIZE, allKeys.length);

    const info = document.createElement("span");
    info.className = "pg-info";
    info.textContent = `${start}–${end} / ${allKeys.length}`;

    const prevBtn = document.createElement("button");
    prevBtn.className = "pg-btn";
    prevBtn.textContent = "← Prev";
    prevBtn.disabled = currentPage === 1;
    prevBtn.addEventListener("click", () => {
        renderPage(currentPage - 1);
        document.getElementById("translation-entry").scrollTop = 0;
    });

    const nextBtn = document.createElement("button");
    nextBtn.className = "pg-btn";
    nextBtn.textContent = "Next →";
    nextBtn.disabled = currentPage === total;
    nextBtn.addEventListener("click", () => {
        renderPage(currentPage + 1);
        document.getElementById("translation-entry").scrollTop = 0;
    });

    const pages = document.createElement("div");
    pages.className = "pagination-pages";

    for (const p of getPageRange(currentPage, total)) {
        if (p === "…") {
            const el = document.createElement("span");
            el.className = "pg-ellipsis";
            el.textContent = "…";
            pages.appendChild(el);
        } else {
            const btn = document.createElement("button");
            btn.className = "pg-btn" + (p === currentPage ? " active" : "");
            btn.textContent = p;
            if (p !== currentPage) {
                btn.addEventListener("click", () => {
                    renderPage(p);
                    document.getElementById("translation-entry").scrollTop = 0;
                });
            }
            pages.appendChild(btn);
        }
    }

    nav.appendChild(prevBtn);
    nav.appendChild(pages);
    nav.appendChild(info);
    nav.appendChild(nextBtn);

    document.getElementById("translation-entry").after(nav);
}

function removePagination() {
    document.querySelectorAll("#pagination").forEach(el => el.remove());
}

/** Window of page numbers with ellipsis collapsing. */
function getPageRange(current, total) {
    if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

    const set = new Set([1, total, current]);
    for (let i = current - 1; i <= current + 1; i++) {
        if (i >= 1 && i <= total) set.add(i);
    }

    const sorted = Array.from(set).sort((a, b) => a - b);
    const result = [];
    let prev = null;
    for (const n of sorted) {
        if (prev !== null && n - prev > 1) result.push("…");
        result.push(n);
        prev = n;
    }
    return result;
}

// ── Download ──────────────────────────────────────────────────────────────────

function downloadTranslation() {
    saveCurrentPage();

    const langCode = selectedLangCode || "unknown";
    const result = {};
    for (const key of allKeys) {
        result[key] = editedValues[key] ?? "";
    }

    // JSON.stringify converts real \n characters back to \n escape sequences — correct.
    const json = JSON.stringify(result, null, 2);
    const blob = new Blob([json], { type: "application/json" });
    const url  = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    a.download = `${langCode}.json`;
    a.click();

    URL.revokeObjectURL(url);
}

// ── Status ────────────────────────────────────────────────────────────────────

/**
 * @param {string} html  - Message HTML (can include <strong> etc.)
 * @param {"ok"|"warn"|"err"|"info"} type
 */
function setStatus(html, type = "info") {
    const el = document.getElementById("translation-status");
    el.innerHTML = html;
    el.className = `status-bar ${type}`;
    el.hidden = false;
}