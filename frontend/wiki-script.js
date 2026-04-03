// ==================== CONFIGURATION ====================

const WIKI_BASE_URL = '/wiki/';  // Served locally and on Vercel via @vercel/static

const DEFAULT_PAGE = 'Home';

// Valid wiki pages - only these pages exist
const VALID_PAGES = [
    'Home', 'Getting-Started', 'FAQ', 'Commands', 'Permissions', 'Configuration',
    'Language', 'Bot-Names', 'Bot-Messages', 'Bot-Behaviour', 'Skin-System',
    'Swap-System', 'Fake-Chat', 'Placeholders', 'Database', 'Migration', 'Changelog'
];


// ==================== STATE ====================

let currentPage = DEFAULT_PAGE;
let wikiContent = {};
let searchIndex = [];

// ==================== THEME ====================

function initTheme() {
    const savedTheme = localStorage.getItem('wiki-theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);
    updateHighlightTheme(savedTheme);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'light' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('wiki-theme', next);
    updateHighlightTheme(next);
}

function updateHighlightTheme(theme) {
    const link = document.getElementById('highlight-theme');
    if (link) {
        link.href = theme === 'dark'
            ? 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css'
            : 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github.min.css';
    }
}

// ==================== NAVIGATION ====================

function initNavigation() {
    // Sidebar links
    document.querySelectorAll('.sidebar-nav a').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const page = link.getAttribute('data-page');
            if (page) {
                loadPage(page);
                setActivePage(page);
                closeMobileMenu();
            }
        });
    });

    // Mobile menu toggle
    const menuToggle = document.getElementById('menuToggle');
    const sidebar = document.getElementById('sidebar');

    if (menuToggle) {
        menuToggle.addEventListener('click', () => {
            if (sidebar.classList.contains('active')) {
                closeMobileMenu();
            } else {
                openMobileMenu();
            }
        });
    }

    // Close menu when clicking outside (fallback — backdrop also handles this)
    document.addEventListener('click', (e) => {
        if (sidebar.classList.contains('active') &&
            !sidebar.contains(e.target) &&
            menuToggle && !menuToggle.contains(e.target)) {
            closeMobileMenu();
        }
    });
}

function openMobileMenu() {
    const sidebar = document.getElementById('sidebar');
    sidebar.classList.add('active');
    // Create backdrop if it doesn't exist yet
    let backdrop = document.getElementById('sidebarBackdrop');
    if (!backdrop) {
        backdrop = document.createElement('div');
        backdrop.id = 'sidebarBackdrop';
        backdrop.className = 'sidebar-backdrop';
        document.body.appendChild(backdrop);
        backdrop.addEventListener('click', closeMobileMenu);
    }
    backdrop.classList.add('active');
}

function closeMobileMenu() {
    const sidebar = document.getElementById('sidebar');
    sidebar.classList.remove('active');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (backdrop) backdrop.classList.remove('active');
}

function setActivePage(page) {
    // Validate page parameter
    if (!page || !VALID_PAGES.includes(page)) {
        console.warn('Attempted to set invalid active page:', page);
        page = DEFAULT_PAGE;
    }

    document.querySelectorAll('.sidebar-nav a').forEach(link => {
        link.classList.remove('active');
        if (link.getAttribute('data-page') === page) {
            link.classList.add('active');
        }
    });
    currentPage = page;
    updateURL(page);
}

function updateURL(page) {
    // Validate page before updating URL
    if (!page || !VALID_PAGES.includes(page)) {
        console.warn('Invalid page for URL update:', page);
        page = DEFAULT_PAGE;
    }

    const url = new URL(window.location);
    url.hash = page;
    window.history.pushState({}, '', url);
}

// ==================== PAGE LOADING ====================

async function loadPage(page) {
    const content = document.getElementById('content');

    // Validate page name first
    if (!page || typeof page !== 'string') {
        showPageNotFound('Invalid page name', content);
        return;
    }

    // Clean the page name - remove any invalid characters
    const cleanPage = page.replace(/[^a-zA-Z0-9-_]/g, '');

    // Check if page exists in our valid pages list
    if (!VALID_PAGES.includes(cleanPage)) {
        showPageNotFound(cleanPage, content);
        return;
    }

    // Show loading state
    content.innerHTML = `
        <div class="loading">
            <div class="spinner"></div>
            <p>Loading ${cleanPage}...</p>
        </div>
    `;

    try {
        // Try to fetch from GitHub with cache busting
        const url = `${WIKI_BASE_URL}${cleanPage}.md`;
        const response = await fetch(url);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: Failed to load ${cleanPage}`);
        }

        const markdown = await response.text();

        // Validate that we got actual markdown content, not HTML
        if (markdown.includes('<!DOCTYPE html>') || markdown.includes('<html>')) {
            throw new Error('Received HTML instead of markdown content');
        }

        // Cache the content
        wikiContent[cleanPage] = markdown;

        // Update current page
        currentPage = cleanPage;

        // Render the page
        renderMarkdown(markdown);

        // Update TOC
        generateTOC();

        // Update page navigation
        updatePageNavigation();

        // Scroll to top
        window.scrollTo({ top: 0, behavior: 'smooth' });

    } catch (error) {
        console.error('Error loading page:', error);
        showLoadError(cleanPage, error.message, content);
    }
}

function showPageNotFound(pageName, content) {
    // Get similar page suggestions
    const suggestions = getSimilarPages(pageName);

    content.innerHTML = `
        <div class="error-page">
            <div class="error-icon">🔍</div>
            <h1>Page Not Found</h1>
            <p class="error-message">The page <strong>"${pageName}"</strong> doesn't exist in our documentation.</p>

            ${suggestions.length > 0 ? `
            <div class="error-suggestions">
                <h3>Did you mean?</h3>
                <ul class="suggestion-list">
                    ${suggestions.map(page => `
                        <li><a href="#${page}" onclick="loadPage('${page}')">${formatPageTitle(page)}</a></li>
                    `).join('')}
                </ul>
            </div>
            ` : ''}

            <div class="error-actions">
                <button onclick="loadPage('${DEFAULT_PAGE}')" class="btn-primary">
                    🏠 Go to Home
                </button>
                <button onclick="showAllPages()" class="btn-secondary">
                    📚 Browse All Pages
                </button>
            </div>
        </div>
    `;
}

function showLoadError(pageName, errorMessage, content) {
    content.innerHTML = `
        <div class="error-page">
            <div class="error-icon">⚠️</div>
            <h1>Loading Failed</h1>
            <p class="error-message">Could not load <strong>"${pageName}"</strong></p>
            <p class="error-details">${errorMessage}</p>

            <div class="error-actions">
                <button onclick="loadPage('${pageName}')" class="btn-primary">
                    🔄 Try Again
                </button>
                <button onclick="loadPage('${DEFAULT_PAGE}')" class="btn-secondary">
                    🏠 Go to Home
                </button>
            </div>
        </div>
    `;
}

function getSimilarPages(input) {
    if (!input) return [];

    const inputLower = input.toLowerCase();
    const similar = [];

    VALID_PAGES.forEach(page => {
        const pageLower = page.toLowerCase();

        // Exact partial match
        if (pageLower.includes(inputLower) || inputLower.includes(pageLower)) {
            similar.push(page);
        }
        // Similar starting letters
        else if (pageLower.startsWith(inputLower.substring(0, 3)) && inputLower.length >= 3) {
            similar.push(page);
        }
    });

    return similar.slice(0, 5); // Return top 5 suggestions
}

function formatPageTitle(page) {
    return page.replace(/-/g, ' ');
}

function showAllPages() {
    const content = document.getElementById('content');

    const pagesByCategory = {
        'Getting Started': ['Home', 'Getting-Started', 'FAQ'],
        'Core Features': ['Commands', 'Permissions', 'Configuration', 'Language'],
        'Bot Systems': ['Bot-Names', 'Bot-Messages', 'Bot-Behaviour', 'Skin-System'],
        'Advanced': ['Swap-System', 'Fake-Chat', 'Placeholders', 'Database', 'Migration'],
        'Release Notes': ['Changelog']
    };

    let html = `
        <div class="all-pages">
            <h1>📚 All Documentation Pages</h1>
            <p>Browse all available documentation pages by category:</p>
    `;

    for (const [category, pages] of Object.entries(pagesByCategory)) {
        html += `
            <div class="page-category">
                <h2>${category}</h2>
                <div class="page-grid">
                    ${pages.map(page => `
                        <a href="#${page}" onclick="loadPage('${page}')" class="page-card">
                            <h3>${formatPageTitle(page)}</h3>
                        </a>
                    `).join('')}
                </div>
            </div>
        `;
    }

    html += `
            <div class="error-actions" style="margin-top: 2rem;">
                <button onclick="loadPage('${DEFAULT_PAGE}')" class="btn-primary">
                    🏠 Back to Home
                </button>
            </div>
        </div>
    `;

    content.innerHTML = html;
}

function renderMarkdown(markdown) {
    const content = document.getElementById('content');

    // Configure marked
    marked.setOptions({
        breaks: true,
        gfm: true,
        headerIds: true,
        mangle: false
    });

    // Parse markdown
    let html = marked.parse(markdown);

    // Sanitize HTML
    html = DOMPurify.sanitize(html, {
        ADD_ATTR: ['target', 'rel'],
        ADD_TAGS: ['iframe']
    });

    // Render
    content.innerHTML = html;

    // Wrap every table in a scroll container for mobile
    content.querySelectorAll('table').forEach(table => {
        if (table.parentElement && table.parentElement.classList.contains('table-wrap')) return;
        const wrap = document.createElement('div');
        wrap.className = 'table-wrap';
        table.parentNode.insertBefore(wrap, table);
        wrap.appendChild(table);
    });

    // Append page navigation
    const pageNavHTML = `
        <nav id="pageNav" class="page-navigation" style="display: none;">
            <a href="#" id="prevPage" class="page-nav-btn prev-page">
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="m15 18-6-6 6-6"/>
                </svg>
                <span class="nav-text">
                    <span class="nav-label">Previous</span>
                    <span class="nav-title"></span>
                </span>
            </a>
            <a href="#" id="nextPage" class="page-nav-btn next-page">
                <span class="nav-text">
                    <span class="nav-label">Next</span>
                    <span class="nav-title"></span>
                </span>
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="m9 18 6-6-6-6"/>
                </svg>
            </a>
        </nav>
    `;
    content.insertAdjacentHTML('beforeend', pageNavHTML);

    // Add target="_blank" to external links
    content.querySelectorAll('a[href^="http"]').forEach(link => {
        link.setAttribute('target', '_blank');
        link.setAttribute('rel', 'noopener noreferrer');
    });

    // Highlight code blocks
    content.querySelectorAll('pre code').forEach(block => {
        hljs.highlightElement(block);
    });

    // Add copy button to code blocks
    content.querySelectorAll('pre').forEach(pre => {
        addCopyButton(pre);
    });

    // Process special blockquotes into alert boxes
    content.querySelectorAll('blockquote').forEach(blockquote => {
        const text = blockquote.textContent.trim();
        if (text.startsWith('Note:') || text.startsWith('**Note:**')) {
            blockquote.classList.add('note');
        } else if (text.startsWith('Warning:') || text.startsWith('**Warning:**')) {
            blockquote.classList.add('warning');
        } else if (text.startsWith('Tip:') || text.startsWith('**Tip:**')) {
            blockquote.classList.add('tip');
        }
    });
}

function addCopyButton(pre) {
    // Skip if button already exists
    if (pre.querySelector('.copy-code-btn')) {
        return;
    }

    const button = document.createElement('button');
    button.className = 'copy-code-btn';
    button.setAttribute('aria-label', 'Copy code to clipboard');
    button.setAttribute('title', 'Copy code');
    button.innerHTML = `
        <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
            <rect x="8" y="2" width="8" height="4" rx="1" ry="1"/>
        </svg>
        Copy
    `;

    // Ensure pre block is positioned relatively
    if (getComputedStyle(pre).position === 'static') {
        pre.style.position = 'relative';
    }

    pre.appendChild(button);

    // Copy functionality with enhanced error handling
    button.addEventListener('click', async (event) => {
        event.preventDefault();
        event.stopPropagation();

        try {
            // Get the code content, preferring code element
            const codeElement = pre.querySelector('code');
            const textToCopy = codeElement ? codeElement.textContent : pre.textContent;

            // Clean up the text (remove copy button text if it got included)
            const cleanText = textToCopy.replace(/^\s*Copy\s*/, '').trim();

            // Try modern clipboard API first
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(cleanText);
                showCopySuccess(button);
            } else {
                // Fallback for older browsers or non-HTTPS
                fallbackCopyToClipboard(cleanText);
                showCopySuccess(button);
            }
        } catch (error) {
            console.error('Copy failed:', error);
            showCopyError(button);
        }
    });

    // Keyboard accessibility
    button.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            button.click();
        }
    });

    // Enhanced hover behavior
    let hoverTimeout;

    pre.addEventListener('mouseenter', () => {
        clearTimeout(hoverTimeout);
        button.style.opacity = '1';
        button.style.visibility = 'visible';
    });

    pre.addEventListener('mouseleave', () => {
        // Delay hiding to prevent flickering
        hoverTimeout = setTimeout(() => {
            if (!button.matches(':focus')) {
                button.style.opacity = '0';
                button.style.visibility = 'hidden';
            }
        }, 100);
    });

    // Keep button visible when focused
    button.addEventListener('focus', () => {
        clearTimeout(hoverTimeout);
        button.style.opacity = '1';
        button.style.visibility = 'visible';
    });

    button.addEventListener('blur', () => {
        if (!pre.matches(':hover')) {
            button.style.opacity = '0';
            button.style.visibility = 'hidden';
        }
    });
}

// Success feedback
function showCopySuccess(button) {
    const originalContent = button.innerHTML;
    button.classList.add('copied');
    button.innerHTML = `
        <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="m9 12 2 2 4-4"/>
        </svg>
        Copied!
    `;

    setTimeout(() => {
        button.classList.remove('copied');
        button.innerHTML = originalContent;
    }, 2000);
}

// Error feedback
function showCopyError(button) {
    const originalContent = button.innerHTML;
    button.style.background = 'var(--danger-color)';
    button.style.borderColor = 'var(--danger-color)';
    button.style.color = 'var(--text-inverse)';
    button.innerHTML = `
        <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="m21 21-6-6m0 0L9 9l6 6"/>
        </svg>
        Failed
    `;

    setTimeout(() => {
        button.style.background = '';
        button.style.borderColor = '';
        button.style.color = '';
        button.innerHTML = originalContent;
    }, 2000);
}

// Fallback copy method for older browsers
function fallbackCopyToClipboard(text) {
    const textArea = document.createElement('textarea');
    textArea.value = text;
    textArea.style.position = 'fixed';
    textArea.style.left = '-999999px';
    textArea.style.top = '-999999px';
    document.body.appendChild(textArea);
    textArea.focus();
    textArea.select();

    try {
        const successful = document.execCommand('copy');
        if (!successful) {
            throw new Error('Copy command failed');
        }
    } finally {
        document.body.removeChild(textArea);
    }
}

// ==================== TABLE OF CONTENTS ====================

function generateTOC() {
    const content = document.getElementById('content');
    const tocNav = document.getElementById('tocNav');
    const headings = content.querySelectorAll('h2, h3, h4');

    if (headings.length === 0) {
        tocNav.innerHTML = '<p class="toc-empty">No headings found</p>';
        return;
    }

    tocNav.innerHTML = '';

    // Add progress indicator
    const progressIndicator = document.createElement('div');
    progressIndicator.className = 'toc-progress';
    tocNav.appendChild(progressIndicator);

    // Track used IDs to prevent duplicates
    const usedIds = new Set();

    headings.forEach((heading, index) => {
        const level = heading.tagName.toLowerCase();
        const text = heading.textContent;
        let id = text.toLowerCase().replace(/[^\w]+/g, '-');

        // Handle duplicate IDs by appending a counter
        let uniqueId = id;
        let counter = 1;
        while (usedIds.has(uniqueId) || document.getElementById(uniqueId)) {
            uniqueId = `${id}-${counter}`;
            counter++;
        }
        usedIds.add(uniqueId);

        // Ensure heading has a unique ID
        heading.id = uniqueId;

        const link = document.createElement('a');
        link.href = `#${uniqueId}`;
        link.textContent = text;
        link.className = `toc-${level}`;
        link.setAttribute('data-index', index);

        link.addEventListener('click', (e) => {
            e.preventDefault();
            heading.scrollIntoView({ behavior: 'smooth', block: 'start' });

            // Update active state immediately
            tocNav.querySelectorAll('a').forEach(a => a.classList.remove('active'));
            link.classList.add('active');

            // Update progress indicator
            updateProgressIndicator(index, headings.length);
        });

        tocNav.appendChild(link);
    });

    // Initialize scroll spy with enhanced features
    initTOCScrollSpy();

    // Initialize progress indicator
    updateProgressIndicator(0, headings.length);
}

// Update reading progress indicator
function updateProgressIndicator(currentIndex, totalHeadings) {
    const progressIndicator = document.querySelector('.toc-progress');
    if (progressIndicator && totalHeadings > 0) {
        const progress = ((currentIndex + 1) / totalHeadings) * 100;
        progressIndicator.style.height = `${progress}%`;
    }
}

function initTOCScrollSpy() {
    const headings = document.querySelectorAll('#content h2, #content h3, #content h4');
    const tocLinks = document.querySelectorAll('#tocNav a');
    const tocNav = document.getElementById('tocNav');

    if (headings.length === 0 || tocLinks.length === 0) return;

    let activeHeading = null;
    let isAutoScrolling = false;

    // Enhanced intersection observer with better threshold detection
    const observer = new IntersectionObserver((entries) => {
        if (isAutoScrolling) return; // Prevent conflicts during auto-scroll

        // Find the most visible heading
        let maxVisibility = 0;
        let mostVisibleHeading = null;

        entries.forEach(entry => {
            if (entry.isIntersecting && entry.intersectionRatio > maxVisibility) {
                maxVisibility = entry.intersectionRatio;
                mostVisibleHeading = entry.target;
            }
        });

        // If no heading is intersecting, find the closest one above the viewport
        if (!mostVisibleHeading) {
            const scrollY = window.scrollY;
            let closestHeading = null;
            let closestDistance = Infinity;

            headings.forEach(heading => {
                const rect = heading.getBoundingClientRect();
                const headingTop = scrollY + rect.top;

                if (headingTop <= scrollY + 100) { // 100px offset for better UX
                    const distance = Math.abs(scrollY - headingTop);
                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestHeading = heading;
                    }
                }
            });

            if (closestHeading) {
                mostVisibleHeading = closestHeading;
            }
        }

        // Update active state and auto-slide TOC
        if (mostVisibleHeading && mostVisibleHeading !== activeHeading) {
            activeHeading = mostVisibleHeading;
            updateTOCActive(activeHeading.id);
            autoSlideTOC(activeHeading.id);
        }
    }, {
        rootMargin: '-80px 0px -50%', // Adjusted for better detection
        threshold: [0, 0.1, 0.25, 0.5, 0.75, 1] // Multiple thresholds for better precision
    });

    // Observe all headings
    headings.forEach(heading => observer.observe(heading));

    // Update active TOC link and progress indicator
    function updateTOCActive(activeId) {
        const headings = document.querySelectorAll('#content h2, #content h3, #content h4');
        let activeIndex = -1;

        tocLinks.forEach((link, index) => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${activeId}`) {
                link.classList.add('active');
                activeIndex = parseInt(link.getAttribute('data-index') || index);
            }
        });

        // Update progress indicator
        if (activeIndex >= 0) {
            updateProgressIndicator(activeIndex, headings.length);
        }
    }

    // Auto-slide TOC to keep active item visible (simplified)
    function autoSlideTOC(activeId) {
        const activeLink = tocNav.querySelector(`a[href="#${activeId}"]`);
        if (!activeLink) return;

        const tocContainer = document.querySelector('.wiki-toc');
        if (!tocContainer) return;

        // Simple scroll behavior - just ensure the active link is visible
        const linkRect = activeLink.getBoundingClientRect();
        const containerRect = tocContainer.getBoundingClientRect();

        // Only scroll if the link is completely outside the visible area
        if (linkRect.bottom > containerRect.bottom || linkRect.top < containerRect.top) {
            activeLink.scrollIntoView({
                behavior: 'smooth',
                block: 'center'
            });
        }
    }

    // Enhanced click handling with smooth scroll coordination
    tocLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();

            const targetId = link.getAttribute('href').substring(1);
            const targetHeading = document.getElementById(targetId);

            if (targetHeading) {
                isAutoScrolling = true;

                // Scroll to the target heading
                targetHeading.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start',
                    inline: 'nearest'
                });

                // Update active state immediately
                updateTOCActive(targetId);
                activeHeading = targetHeading;

                // Reset auto-scrolling flag after animation
                setTimeout(() => {
                    isAutoScrolling = false;
                }, 1000);
            }
        });
    });

    // Enhanced scroll listener for immediate feedback
    let scrollTimeout;
    window.addEventListener('scroll', () => {
        clearTimeout(scrollTimeout);
        scrollTimeout = setTimeout(() => {
            if (isAutoScrolling) return;

            // Force update if no intersection is detected (e.g., at page bottom)
            const scrollY = window.scrollY;
            const windowHeight = window.innerHeight;
            const documentHeight = document.documentElement.scrollHeight;

            // If we're near the bottom of the page, activate the last heading
            if (scrollY + windowHeight >= documentHeight - 100) {
                const lastHeading = headings[headings.length - 1];
                if (lastHeading && lastHeading !== activeHeading) {
                    activeHeading = lastHeading;
                    updateTOCActive(lastHeading.id);
                    autoSlideTOC(lastHeading.id);
                }
            }
        }, 50); // Debounce for performance
    });

    // Initialize with first heading if at top of page
    if (window.scrollY < 100 && headings.length > 0) {
        activeHeading = headings[0];
        updateTOCActive(headings[0].id);
    }
}

// ==================== SEARCH ====================

function initSearch() {
    const searchBtn = document.getElementById('searchBtn');
    const searchModal = document.getElementById('searchModal');
    const closeSearch = document.getElementById('closeSearch');
    const searchInput = document.getElementById('searchInput');

    // Open search
    searchBtn.addEventListener('click', openSearch);

    // Close search
    closeSearch.addEventListener('click', closeSearchModal);

    // Close on outside click
    searchModal.addEventListener('click', (e) => {
        if (e.target === searchModal) {
            closeSearchModal();
        }
    });

    // Close on Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && searchModal.classList.contains('active')) {
            closeSearchModal();
        }
    });

    // Ctrl+K to open search
    document.addEventListener('keydown', (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            e.preventDefault();
            openSearch();
        }
    });

    // Search input
    let searchTimeout;
    searchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            performSearch(e.target.value);
        }, 300);
    });
}

function openSearch() {
    const searchModal = document.getElementById('searchModal');
    const searchInput = document.getElementById('searchInput');
    searchModal.classList.add('active');
    searchInput.focus();
    buildSearchIndex();
}

function closeSearchModal() {
    const searchModal = document.getElementById('searchModal');
    const searchInput = document.getElementById('searchInput');
    const searchResults = document.getElementById('searchResults');
    searchModal.classList.remove('active');
    searchInput.value = '';
    searchResults.innerHTML = '<div class="search-hint">Start typing to search...</div>';
}

async function buildSearchIndex() {
    if (searchIndex.length > 0) return; // Already built

    const pages = [
        'Home', 'Getting-Started', 'FAQ', 'Commands', 'Permissions',
        'Configuration', 'Language', 'Bot-Names', 'Bot-Messages',
        'Bot-Behaviour', 'Skin-System', 'Swap-System', 'Fake-Chat',
        'Placeholders', 'Database', 'Migration', 'Changelog'
    ];

    for (const page of pages) {
        try {
            if (!wikiContent[page]) {
                const response = await fetch(`${WIKI_BASE_URL}${page}.md`);
                if (response.ok) {
                    wikiContent[page] = await response.text();
                }
            }

            if (wikiContent[page]) {
                // Index page content
                const lines = wikiContent[page].split('\n');
                lines.forEach((line, index) => {
                    if (line.trim()) {
                        searchIndex.push({
                            page,
                            line: index,
                            content: line.replace(/[#*`]/g, '').trim()
                        });
                    }
                });
            }
        } catch (error) {
            console.error(`Failed to index ${page}:`, error);
        }
    }
}

function performSearch(query) {
    const searchResults = document.getElementById('searchResults');

    if (!query || query.length < 2) {
        searchResults.innerHTML = '<div class="search-hint">Type at least 2 characters to search...</div>';
        return;
    }

    const results = [];
    const queryLower = query.toLowerCase();
    const seen = new Set();

    searchIndex.forEach(item => {
        if (item.content.toLowerCase().includes(queryLower)) {
            const key = `${item.page}:${item.line}`;
            if (!seen.has(key)) {
                seen.add(key);
                results.push(item);
            }
        }
    });

    if (results.length === 0) {
        searchResults.innerHTML = `
            <div class="search-hint">
                No results found for "<strong>${escapeHtml(query)}</strong>"
            </div>
        `;
        return;
    }

    // Group by page
    const grouped = {};
    results.forEach(item => {
        if (!grouped[item.page]) {
            grouped[item.page] = [];
        }
        grouped[item.page].push(item);
    });

    // Render results
    let html = '';
    Object.keys(grouped).slice(0, 10).forEach(page => {
        const items = grouped[page].slice(0, 3);
        items.forEach(item => {
            const excerpt = highlightMatch(item.content, query);
            html += `
                <div class="search-result" onclick="navigateToResult('${page}')">
                    <div class="search-result-title">${formatPageTitle(page)}</div>
                    <div class="search-result-excerpt">${excerpt}</div>
                </div>
            `;
        });
    });

    searchResults.innerHTML = html;
}

function highlightMatch(text, query) {
    const index = text.toLowerCase().indexOf(query.toLowerCase());
    if (index === -1) return escapeHtml(text);

    const start = Math.max(0, index - 40);
    const end = Math.min(text.length, index + query.length + 40);

    let excerpt = text.substring(start, end);
    if (start > 0) excerpt = '...' + excerpt;
    if (end < text.length) excerpt = excerpt + '...';

    const regex = new RegExp(`(${escapeRegex(query)})`, 'gi');
    excerpt = escapeHtml(excerpt).replace(regex, '<mark>$1</mark>');

    return excerpt;
}

function navigateToResult(page) {
    closeSearchModal();
    loadPage(page);
    setActivePage(page);
}

function formatPageTitle(page) {
    return page.replace(/-/g, ' ');
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// ==================== MOBILE TOC ====================

function initMobileTOC() {
    // Create mobile TOC toggle button
    const mobileTOCToggle = document.createElement('button');
    mobileTOCToggle.className = 'mobile-toc-toggle';
    mobileTOCToggle.innerHTML = `
        <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M3 12h18M3 6h18M3 18h18"/>
        </svg>
    `;
    mobileTOCToggle.title = 'Table of Contents';
    mobileTOCToggle.setAttribute('aria-label', 'Toggle table of contents');

    document.body.appendChild(mobileTOCToggle);

    const tocElement = document.getElementById('toc');
    let backdrop = null;

    // Toggle mobile TOC
    mobileTOCToggle.addEventListener('click', () => {
        const isActive = tocElement.classList.contains('mobile-active');

        if (isActive) {
            // Close TOC
            tocElement.classList.remove('mobile-active');
            if (backdrop) {
                backdrop.remove();
                backdrop = null;
            }
            mobileTOCToggle.innerHTML = `
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="M3 12h18M3 6h18M3 18h18"/>
                </svg>
            `;
        } else {
            // Open TOC
            tocElement.classList.add('mobile-active');

            // Create backdrop
            backdrop = document.createElement('div');
            backdrop.className = 'mobile-toc-backdrop';
            document.body.appendChild(backdrop);

            // Close on backdrop click
            backdrop.addEventListener('click', () => {
                tocElement.classList.remove('mobile-active');
                backdrop.remove();
                backdrop = null;
                mobileTOCToggle.innerHTML = `
                    <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                        <path d="M3 12h18M3 6h18M3 18h18"/>
                    </svg>
                `;
            });

            mobileTOCToggle.innerHTML = `
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
            `;
        }
    });

    // Close mobile TOC when clicking on any TOC link
    document.addEventListener('click', (e) => {
        if (e.target.closest('.toc-nav a')) {
            tocElement.classList.remove('mobile-active');
            if (backdrop) {
                backdrop.remove();
                backdrop = null;
            }
            mobileTOCToggle.innerHTML = `
                <svg width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path d="M3 12h18M3 6h18M3 18h18"/>
                </svg>
            `;
        }
    });

    // Show/hide toggle based on screen size and content availability
    function updateMobileTOCVisibility() {
        const hasContent = document.querySelectorAll('#tocNav a').length > 0;
        const isMobileScreen = window.innerWidth <= 1200;

        if (hasContent && isMobileScreen) {
            mobileTOCToggle.style.display = 'flex';
        } else {
            mobileTOCToggle.style.display = 'none';
            // Close TOC if it's open
            tocElement.classList.remove('mobile-active');
            if (backdrop) {
                backdrop.remove();
                backdrop = null;
            }
        }
    }

    // Initial check
    updateMobileTOCVisibility();

    // Check on resize
    window.addEventListener('resize', updateMobileTOCVisibility);

    // Check when TOC content changes
    const observer = new MutationObserver(updateMobileTOCVisibility);
    observer.observe(document.getElementById('tocNav'), { childList: true });
}

function initBackToTop() {
    const button = document.getElementById('backToTop');

    window.addEventListener('scroll', () => {
        if (window.scrollY > 400) {
            button.classList.add('visible');
        } else {
            button.classList.remove('visible');
        }
    });

    button.addEventListener('click', () => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    });
}

// ==================== PAGE NAVIGATION ====================

// Define page order for prev/next navigation
const PAGE_ORDER = [
    'Home',
    'Getting-Started',
    'FAQ',
    'Commands',
    'Permissions',
    'Configuration',
    'Language',
    'Bot-Names',
    'Bot-Messages',
    'Bot-Behaviour',
    'Skin-System',
    'Swap-System',
    'Fake-Chat',
    'Placeholders',
    'Database',
    'Migration',
    'Changelog'
];

function updatePageNavigation() {
    const pageNav = document.getElementById('pageNav');
    const prevBtn = document.getElementById('prevPage');
    const nextBtn = document.getElementById('nextPage');

    const currentIndex = PAGE_ORDER.indexOf(currentPage);

    if (currentIndex === -1) {
        pageNav.style.display = 'none';
        return;
    }

    // Hide both buttons initially
    prevBtn.style.display = 'none';
    nextBtn.style.display = 'none';

    // Show prev button if not first page
    if (currentIndex > 0) {
        const prevPage = PAGE_ORDER[currentIndex - 1];
        prevBtn.style.display = 'flex';
        prevBtn.href = `#${prevPage}`;
        prevBtn.querySelector('.nav-title').textContent = formatPageTitle(prevPage);
        prevBtn.onclick = (e) => {
            e.preventDefault();
            loadPage(prevPage);
            setActivePage(prevPage);
        };
    }

    // Show next button if not last page
    if (currentIndex < PAGE_ORDER.length - 1) {
        const nextPage = PAGE_ORDER[currentIndex + 1];
        nextBtn.style.display = 'flex';
        nextBtn.href = `#${nextPage}`;
        nextBtn.querySelector('.nav-title').textContent = formatPageTitle(nextPage);
        nextBtn.onclick = (e) => {
            e.preventDefault();
            loadPage(nextPage);
            setActivePage(nextPage);
        };
    }

    // Show navigation if at least one button is visible
    if (prevBtn.style.display !== 'none' || nextBtn.style.display !== 'none') {
        pageNav.style.display = 'flex';
    } else {
        pageNav.style.display = 'none';
    }
}

// ==================== INITIALIZATION ====================

document.addEventListener('DOMContentLoaded', () => {
    // Initialize theme
    initTheme();

    // Theme toggle
    document.getElementById('themeToggle').addEventListener('click', toggleTheme);

    // Initialize navigation
    initNavigation();

    // Initialize search
    initSearch();

    // Initialize back to top
    initBackToTop();

    // Initialize mobile TOC
    initMobileTOC();

    // Load initial page from URL hash or default
    const hash = window.location.hash.substring(1);
    let initialPage = hash || DEFAULT_PAGE;

    // Validate the initial page - if invalid, redirect to home
    if (!VALID_PAGES.includes(initialPage)) {
        console.warn('Invalid page in URL hash:', initialPage);
        initialPage = DEFAULT_PAGE;
        // Update URL to remove invalid hash
        window.history.replaceState({}, '', window.location.pathname + '#' + DEFAULT_PAGE);
    }

    loadPage(initialPage);
    setActivePage(initialPage);

    // Handle browser back/forward with validation
    window.addEventListener('popstate', () => {
        const hash = window.location.hash.substring(1);
        let page = hash || DEFAULT_PAGE;

        // Validate page from browser navigation
        if (!VALID_PAGES.includes(page)) {
            console.warn('Invalid page from browser navigation:', page);
            page = DEFAULT_PAGE;
            // Silently correct the URL
            window.history.replaceState({}, '', window.location.pathname + '#' + DEFAULT_PAGE);
        }

        loadPage(page);
        setActivePage(page);
    });

    // Handle hash changes (for direct hash modifications)
    window.addEventListener('hashchange', () => {
        const hash = window.location.hash.substring(1);
        let page = hash || DEFAULT_PAGE;

        // Validate page from hash change
        if (!VALID_PAGES.includes(page)) {
            console.warn('Invalid page from hash change:', page);
            page = DEFAULT_PAGE;
            // Redirect to home page
            window.location.hash = DEFAULT_PAGE;
            return;
        }

        loadPage(page);
        setActivePage(page);
    });
});

