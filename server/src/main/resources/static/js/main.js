"use strict";

const normalizeText = (value) => value
    .toLocaleLowerCase("es")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();

const setFormStatus = (element, message, isError = false) => {
    if (!element) {
        return;
    }

    element.textContent = message;
    element.classList.toggle("is-error", isError);
};

const validateForm = (form) => {
    const isValid = form.checkValidity();
    form.classList.add("was-validated");
    return isValid;
};

const filterJobs = (searchTerm) => {
    const normalizedTerm = normalizeText(searchTerm);
    const jobCards = [...document.querySelectorAll("[data-job-card]")];
    const emptyState = document.querySelector("#job-empty-state");
    const resultsCount = document.querySelector("#search-results-count");
    let visibleJobs = 0;

    jobCards.forEach((jobCard) => {
        const keywords = normalizeText(jobCard.dataset.keywords || "");
        const isVisible = !normalizedTerm || keywords.includes(normalizedTerm);
        jobCard.classList.toggle("d-none", !isVisible);

        if (isVisible) {
            visibleJobs += 1;
        }
    });

    emptyState?.classList.toggle("d-none", visibleJobs > 0);

    if (resultsCount) {
        if (!normalizedTerm) {
            resultsCount.textContent = "";
        } else if (visibleJobs === 1) {
            resultsCount.textContent = `Encontramos 1 oportunidad para “${searchTerm.trim()}”.`;
        } else {
            resultsCount.textContent = `Encontramos ${visibleJobs} oportunidades para “${searchTerm.trim()}”.`;
        }
    }
};

const initializeJobSearch = () => {
    const searchForm = document.querySelector("#job-search-form");
    const searchInput = document.querySelector("#job-search-input");
    const clearSearchButton = document.querySelector("#clear-search-button");
    const categoryLinks = document.querySelectorAll("[data-search-term]");
    const resultsSection = document.querySelector("#oportunidades-destacadas");

    searchForm?.addEventListener("submit", (event) => {
        event.preventDefault();

        if (!validateForm(searchForm) || !searchInput) {
            return;
        }

        filterJobs(searchInput.value);
        resultsSection?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    clearSearchButton?.addEventListener("click", () => {
        if (searchInput) {
            searchInput.value = "";
        }

        searchForm?.classList.remove("was-validated");
        filterJobs("");
    });

    categoryLinks.forEach((categoryLink) => {
        categoryLink.addEventListener("click", () => {
            const searchTerm = categoryLink.dataset.searchTerm || "";

            if (searchInput) {
                searchInput.value = searchTerm;
            }

            filterJobs(searchTerm);
        });
    });
};

const initializeNewsletter = () => {
    const newsletterForm = document.querySelector("#newsletter-form");
    const newsletterStatus = document.querySelector("#newsletter-status");

    newsletterForm?.addEventListener("submit", (event) => {
        event.preventDefault();

        if (!newsletterForm.checkValidity()) {
            setFormStatus(newsletterStatus, "Ingresa un correo electrónico válido.", true);
            return;
        }

        setFormStatus(newsletterStatus, "La suscripción aún no está disponible. No se ha registrado tu correo.");
    });
};

const initializeSavedJobs = () => {
    const saveButtons = document.querySelectorAll(".save-job-button");

    saveButtons.forEach((saveButton) => {
        saveButton.addEventListener("click", () => {
            const icon = saveButton.querySelector("i");
            const isSaved = saveButton.classList.toggle("is-saved");

            icon?.classList.toggle("bi-bookmark", !isSaved);
            icon?.classList.toggle("bi-bookmark-fill", isSaved);
            saveButton.setAttribute("aria-pressed", String(isSaved));
        });
    });
};

const initializeResponsiveMenu = () => {
    const navbarElement = document.querySelector("#main-navbar");
    const navLinks = navbarElement?.querySelectorAll("a[href^='#'], a[href*='/#']") || [];

    navLinks.forEach((navLink) => {
        navLink.addEventListener("click", () => {
            if (!navbarElement?.classList.contains("show") || typeof bootstrap === "undefined") {
                return;
            }

            bootstrap.Collapse.getOrCreateInstance(navbarElement).hide();
        });
    });
};

const setCurrentYear = () => {
    const currentYear = document.querySelector("#current-year");

    if (currentYear) {
        currentYear.textContent = String(new Date().getFullYear());
    }
};

document.addEventListener("DOMContentLoaded", () => {
    initializeJobSearch();
    initializeNewsletter();
    initializeSavedJobs();
    initializeResponsiveMenu();
    setCurrentYear();
});
