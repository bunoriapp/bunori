/**
 * Bunori Native Reader Engine
 * Modular, high-performance DOM reader supporting continuous bidirectional
 * infinite scrolling and multi-column paged e-reader mode.
 * Chapters flow continuously into the DOM without jarring reloads.
 */
(function () {
  'use strict';

  const bridge = window.BunoriBridge || {
    onCenterTap: () => console.log('[Bridge Mock] onCenterTap'),
    onProgressUpdate: (id, pct) => console.log('[Bridge Mock] onProgress', id, pct),
    onChapterCompleted: (id) => console.log('[Bridge Mock] onChapterCompleted', id),
    onActiveChapterChanged: (id, title, idx) => console.log('[Bridge Mock] onActiveChapterChanged', id, title, idx),
    onRequestNextChapter: (id) => console.log('[Bridge Mock] onRequestNextChapter', id),
    onRequestPreviousChapter: (id) => console.log('[Bridge Mock] onRequestPreviousChapter', id),
    onLinkClick: (url) => console.log('[Bridge Mock] onLinkClick', url)
  };

  const MAX_DOM_CHAPTERS = 4;

  const state = {
    readingMode: 'CONTINUOUS',
    isPagedMode: false,
    currentPage: 0,
    totalPages: 1,
    activeChapterId: null,
    chapters: new Map(), // chapterId -> { id, title, index }
    isLoadingNext: false,
    isLoadingPrev: false,
    hasMoreNext: true,
    hasMorePrev: true,
    pendingTurnToPrevious: false,
    userHasScrolled: false,
    customJs: ''
  };

  function runUserCustomJs() {
    if (!state.customJs) return;
    try {
      const fn = new Function(state.customJs);
      fn();
    } catch (e) {
      console.error('Custom user JS error:', e);
    }
  }

  const elements = {
    root: document.getElementById('reader-root'),
    container: document.getElementById('chapters-container'),
    topSentinel: document.getElementById('top-sentinel'),
    bottomSentinel: document.getElementById('bottom-sentinel'),
    userCustomStyle: document.getElementById('user-custom-css'),
    userCustomScript: document.getElementById('user-custom-js')
  };

  function decodeB64(str) {
    if (!str) return '';
    try {
      const binString = atob(str);
      const bytes = Uint8Array.from(binString, (m) => m.codePointAt(0));
      return new TextDecoder('utf-8').decode(bytes);
    } catch (e) {
      console.error('Base64 decode error:', e);
      return '';
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function formatChapterHeading(index, title) {
    if (index !== undefined && index !== null && index > 0) {
      if (title && title.toLowerCase().startsWith('chapter')) {
        return title;
      } else if (title && title.trim().length > 0) {
        return `Chapter ${index} - ${title}`;
      } else {
        return `Chapter ${index}`;
      }
    }
    return title || 'Chapter';
  }

  function setSentinelState(sentinel, status, text) {
    if (!sentinel) return;
    const textEl = sentinel.querySelector('.sentinel-text');
    if (textEl && text !== undefined) {
      textEl.textContent = text;
    }
    sentinel.classList.remove('loading', 'boundary');
    if (status === 'loading') {
      sentinel.classList.add('loading');
    } else if (status === 'boundary') {
      sentinel.classList.add('boundary');
    }
  }

  function getFirstChapterInfo() {
    const first = elements.container.querySelector('.chapter-wrapper');
    if (!first) return null;
    return {
      id: parseInt(first.dataset.chapterId, 10),
      title: first.dataset.chapterTitle,
      index: parseInt(first.dataset.chapterIndex, 10)
    };
  }

  function getLastChapterInfo() {
    const wrappers = elements.container.querySelectorAll('.chapter-wrapper');
    if (!wrappers.length) return null;
    const last = wrappers[wrappers.length - 1];
    return {
      id: parseInt(last.dataset.chapterId, 10),
      title: last.dataset.chapterTitle,
      index: parseInt(last.dataset.chapterIndex, 10)
    };
  }

  function createChapterElement(data) {
    const wrapper = document.createElement('div');
    wrapper.className = 'chapter-wrapper';
    wrapper.id = 'chapter-' + data.id;
    wrapper.dataset.chapterId = data.id;
    wrapper.dataset.chapterTitle = data.title || '';
    wrapper.dataset.chapterIndex = data.index || 0;
    wrapper.dataset.chapterScanlator = data.scanlator || '';

    const headingText = formatChapterHeading(data.index, data.title);
    const scanlatorHtml = data.scanlator ? `<div class="chapter-card-scanlator">${escapeHtml(data.scanlator)}</div>` : '';

    wrapper.innerHTML = `
      <div class="chapter-card chapter-card-start">
        <div class="chapter-card-label">Start:</div>
        <div class="chapter-card-title">${escapeHtml(headingText)}</div>
        ${scanlatorHtml}
      </div>
      <div class="chapter-body">
        ${data.htmlContent || ''}
      </div>
      <div class="chapter-card chapter-card-end">
        <div class="chapter-card-label">End:</div>
        <div class="chapter-card-title">${escapeHtml(headingText)}</div>
        ${scanlatorHtml}
      </div>
    `;

    // Observe end card to immediately complete chapter when scrolled to end
    const endCard = wrapper.querySelector('.chapter-card-end');
    if (endCard) endCardObserver.observe(endCard);

    // Recalculate paged layout when images finish loading
    wrapper.querySelectorAll('img').forEach((img) => {
      img.addEventListener('load', () => {
        if (state.isPagedMode) {
          recalculatePages();
        }
      });
    });

    return wrapper;
  }

  // ==========================================================================
  // Active Chapter & Progress Tracking (Unified for Continuous & Paged)
  // ==========================================================================
  const endCardObserver = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        const wrapper = entry.target.closest('.chapter-wrapper');
        if (wrapper) {
          const id = parseInt(wrapper.dataset.chapterId, 10);
          if (id) {
            bridge.onChapterCompleted(id);
          }
        }
      }
    }
  }, { threshold: 0.05 });

  function updateActiveChapterAndProgress() {
    const centerEl = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
    const currentWrapper = centerEl ? centerEl.closest('.chapter-wrapper') : null;
    const wrappers = elements.container.querySelectorAll('.chapter-wrapper');
    if (!wrappers.length) return;
    const activeWrapper = currentWrapper || wrappers[0];

    const id = parseInt(activeWrapper.dataset.chapterId, 10);
    const title = activeWrapper.dataset.chapterTitle || '';
    const idx = parseInt(activeWrapper.dataset.chapterIndex, 10) || 0;

    if (id && state.activeChapterId !== id) {
      state.activeChapterId = id;
      bridge.onActiveChapterChanged(id, title, idx);
    }

    if (state.isPagedMode) {
      const clientWidth = window.innerWidth || 1;
      const chapterStartPage = Math.round(activeWrapper.offsetLeft / clientWidth);
      const chapterPages = Math.max(1, Math.round(activeWrapper.scrollWidth / clientWidth));
      const chapterEndPage = chapterStartPage + chapterPages - 1;

      const progress = Math.min(1.0, Math.max(0, (state.currentPage - chapterStartPage + 1) / chapterPages));
      bridge.onProgressUpdate(id, progress);
      if (state.currentPage >= chapterEndPage) {
        bridge.onChapterCompleted(id);
      }
    } else {
      const rect = activeWrapper.getBoundingClientRect();
      const viewportHeight = window.innerHeight;
      const chapterHeight = rect.height;
      if (chapterHeight > 0) {
        const scrolledDistance = -rect.top;
        const scrollableRange = chapterHeight - viewportHeight;
        const progress = scrollableRange <= 0 ? 1.0 : Math.min(1, Math.max(0, scrolledDistance / scrollableRange));
        bridge.onProgressUpdate(id, progress);
        if (progress >= 0.85) {
          bridge.onChapterCompleted(id);
        }
      }
    }
  }

  function showPagedLoader(text) {
    if (!state.isPagedMode) return;
    const el = document.getElementById('paged-loader');
    if (el) {
      const span = el.querySelector('span');
      if (span && text) span.textContent = text;
      el.classList.add('visible');
    }
  }

  function hidePagedLoader() {
    const el = document.getElementById('paged-loader');
    if (el) el.classList.remove('visible');
  }

  // ==========================================================================
  // Continuous Scroll Observers (Only active in Continuous Mode)
  // ==========================================================================
  let bottomObserver = null;
  let topObserver = null;

  function updateTopSentinelBanner() {
    if (state.isPagedMode) return;
    if (state.hasMorePrev && !state.isLoadingPrev) {
      setSentinelState(elements.topSentinel, 'idle', '');
      let banner = elements.topSentinel.querySelector('.prev-chapter-banner');
      banner.style.display = 'flex';
    } else {
      const banner = elements.topSentinel.querySelector('.prev-chapter-banner');
      if (banner) banner.style.display = 'none';
    }
  }

  function initObservers() {
    // Bottom Sentinel (Preload Next Chapter when scrolling downwards)
    bottomObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && !state.isLoadingNext && state.hasMoreNext && !state.isPagedMode) {
          const last = getLastChapterInfo();
          if (last) {
            state.isLoadingNext = true;
            document.body.classList.add('loading-next');
            setSentinelState(elements.bottomSentinel, 'loading', 'Loading next chapter...');
            bridge.onRequestNextChapter(last.id);
          }
        }
      }
    }, { rootMargin: '200px 0px', threshold: 0.01 });

    if (elements.bottomSentinel) bottomObserver.observe(elements.bottomSentinel);

    // Top Sentinel (Preload Previous Chapter when scrolling upwards)
    topObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && !state.isLoadingPrev && state.hasMorePrev && !state.isPagedMode) {
          if (window.scrollY < 10 && !state.userHasScrolled) return;
          const first = getFirstChapterInfo();
          if (first) {
            state.isLoadingPrev = true;
            setSentinelState(elements.topSentinel, 'loading', 'Loading previous chapter...');
            bridge.onRequestPreviousChapter(first.id);
          }
        }
      }
    }, { rootMargin: '200px 0px', threshold: 0.01 });

    if (elements.topSentinel) topObserver.observe(elements.topSentinel);
  }

  // ==========================================================================
  // Sliding Window Memory Management (Keep at most MAX_DOM_CHAPTERS in DOM)
  // ==========================================================================
  function pruneOldestChapter() {
    const wrappers = elements.container.querySelectorAll('.chapter-wrapper');
    if (wrappers.length <= MAX_DOM_CHAPTERS) return;

    const firstWrapper = wrappers[0];
    const removedId = parseInt(firstWrapper.dataset.chapterId, 10);
    if (!removedId) return;

    if (state.isPagedMode) {
      const prevTotal = state.totalPages;
      elements.container.removeChild(firstWrapper);
      state.chapters.delete(removedId);

      const clientWidth = window.innerWidth;
      const newTotal = Math.max(1, Math.round(elements.container.scrollWidth / clientWidth));
      state.totalPages = newTotal;
      const removedPages = prevTotal - newTotal;

      if (removedPages > 0) {
        state.currentPage = Math.max(0, state.currentPage - removedPages);
        elements.container.style.transition = 'none';
        applyPageTransform();
        void elements.container.offsetHeight;
        elements.container.style.transition = '';
      }
      updateActiveChapterAndProgress();
    } else {
      // In Continuous mode: compensate scroll position as document top shrinks
      const prevScrollHeight = document.documentElement.scrollHeight;
      elements.container.removeChild(firstWrapper);
      state.chapters.delete(removedId);
      const newScrollHeight = document.documentElement.scrollHeight;
      const heightDiff = prevScrollHeight - newScrollHeight;

      if (heightDiff > 0) {
        window.scrollTo({ top: Math.max(0, window.scrollY - heightDiff), behavior: 'instant' });
      }
      void document.body.offsetHeight;
    }

    setSentinelState(elements.topSentinel, 'idle', '');
    state.hasMorePrev = true;
  }

  function pruneNewestChapter() {
    const wrappers = elements.container.querySelectorAll('.chapter-wrapper');
    if (wrappers.length <= MAX_DOM_CHAPTERS) return;

    const lastWrapper = wrappers[wrappers.length - 1];
    const removedId = parseInt(lastWrapper.dataset.chapterId, 10);
    if (!removedId) return;

    elements.container.removeChild(lastWrapper);
    state.chapters.delete(removedId);

    if (state.isPagedMode) {
      const clientWidth = window.innerWidth;
      state.totalPages = Math.max(1, Math.round(elements.container.scrollWidth / clientWidth));
      updateActiveChapterAndProgress();
    }

    setSentinelState(elements.bottomSentinel, 'idle', '');
    state.hasMoreNext = true;
  }

  // ==========================================================================
  // Paged E-Reader Engine (Multi-Column Horizontal Navigation)
  // ==========================================================================
  function recalculatePages() {
    if (!state.isPagedMode) return;
    const scrollWidth = elements.container.scrollWidth;
    const clientWidth = window.innerWidth;
    if (clientWidth <= 0) return;
    state.totalPages = Math.max(1, Math.round(scrollWidth / clientWidth));
    if (state.currentPage >= state.totalPages) {
      state.currentPage = Math.max(0, state.totalPages - 1);
    }
    applyPageTransform();
  }

  function applyPageTransform() {
    if (state.isPagedMode) {
      elements.container.style.transform = `translateX(-${state.currentPage * 100}vw)`;
    } else {
      elements.container.style.transform = '';
    }
  }

  function pageTurn(direction) {
    if (!state.isPagedMode) return;
    const newPage = state.currentPage + direction;

    // Boundary at the start of loaded chapters
    if (newPage < 0) {
      if (state.hasMorePrev) {
        state.pendingTurnToPrevious = true;
        if (!state.isLoadingPrev) {
          const first = getFirstChapterInfo();
          if (first) {
            state.isLoadingPrev = true;
            showPagedLoader('Loading previous chapter...');
            bridge.onRequestPreviousChapter(first.id);
          }
        }
      }
      return;
    }

    // Boundary at the end of loaded chapters
    if (newPage >= state.totalPages) {
      if (state.hasMoreNext && !state.isLoadingNext) {
        const last = getLastChapterInfo();
        if (last) {
          state.isLoadingNext = true;
          document.body.classList.add('loading-next');
          showPagedLoader('Loading next chapter...');
          bridge.onRequestNextChapter(last.id);
        }
      }
      return;
    }

    // Smooth page flip
    state.currentPage = newPage;
    applyPageTransform();
    updateActiveChapterAndProgress();

    // Silently preload next chapter when approaching the end of loaded pages
    if (state.currentPage >= state.totalPages - 2 && state.hasMoreNext && !state.isLoadingNext) {
      const last = getLastChapterInfo();
      if (last) {
        state.isLoadingNext = true;
        document.body.classList.add('loading-next');
        showPagedLoader('Loading next chapter...');
        bridge.onRequestNextChapter(last.id);
      }
    }

    // Silently preload previous chapter when approaching the start of loaded pages
    if (state.currentPage <= 1 && state.hasMorePrev && !state.isLoadingPrev) {
      const first = getFirstChapterInfo();
      if (first) {
        state.isLoadingPrev = true;
        showPagedLoader('Loading previous chapter...');
        bridge.onRequestPreviousChapter(first.id);
      }
    }
  }

  window.pageTurn = pageTurn;

  // ==========================================================================
  // Chapter Injection API (Called by Kotlin evaluateJavascript)
  // ==========================================================================
  window.setInitialChapter = function (chapterData, startAtEnd) {
    if (!chapterData || !chapterData.id) return;
    const initialLoader = document.getElementById('initial-loader');
    if (initialLoader) initialLoader.remove();
    hidePagedLoader();

    document.body.classList.remove('loading-next');
    elements.container.innerHTML = '';
    state.chapters.clear();
    state.isLoadingNext = false;
    state.isLoadingPrev = false;
    state.pendingTurnToPrevious = false;
    state.userHasScrolled = false;
    state.currentPage = 0;
    state.activeChapterId = chapterData.id;

    setSentinelState(elements.topSentinel, 'idle', '');
    setSentinelState(elements.bottomSentinel, 'idle', '');

    appendChapter(chapterData);

    bridge.onActiveChapterChanged(chapterData.id, chapterData.title || '', chapterData.index || 0);

    if (state.isPagedMode) {
      recalculatePages();
      if (startAtEnd) {
        state.currentPage = Math.max(0, state.totalPages - 1);
      } else {
        state.currentPage = 0;
      }
      applyPageTransform();
      updateActiveChapterAndProgress();
      // Silently preload next chapter if available
      if (!startAtEnd && state.hasMoreNext && !state.isLoadingNext) {
        state.isLoadingNext = true;
        document.body.classList.add('loading-next');
        showPagedLoader('Loading next chapter...');
        bridge.onRequestNextChapter(chapterData.id);
      } else if (startAtEnd && state.hasMorePrev && !state.isLoadingPrev) {
        state.isLoadingPrev = true;
        showPagedLoader('Loading previous chapter...');
        bridge.onRequestPreviousChapter(chapterData.id);
      }
    } else {
      if (startAtEnd) {
        window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'instant' });
      } else {
        window.scrollTo({ top: 0, behavior: 'instant' });
      }
      bridge.onProgressUpdate(chapterData.id, startAtEnd ? 1.0 : 0);
    }
    updateTopSentinelBanner();
  };

  window.setInitialChapterB64 = function (id, b64Title, index, b64Html, startAtEnd, b64Scanlator) {
    window.setInitialChapter({
      id: id,
      title: decodeB64(b64Title),
      index: index,
      htmlContent: decodeB64(b64Html),
      scanlator: decodeB64(b64Scanlator)
    }, startAtEnd === true);
  };

  window.appendChapter = function (chapterData) {
    state.isLoadingNext = false;
    document.body.classList.remove('loading-next');
    if (!chapterData || !chapterData.id) return;
    if (state.chapters.has(chapterData.id)) return; // Prevent duplicates

    // Capture the exact scroll position before appending to prevent jumping to the end
    const savedScrollY = window.scrollY;

    const wrapper = createChapterElement(chapterData);
    elements.container.appendChild(wrapper);
    state.chapters.set(chapterData.id, chapterData);

    runUserCustomJs();

    if (!state.activeChapterId) state.activeChapterId = chapterData.id;
    if (state.hasMoreNext) setSentinelState(elements.bottomSentinel, 'idle', '');

    if (state.isPagedMode) {
      recalculatePages();
      updateActiveChapterAndProgress();
      hidePagedLoader();
    } else {
      // Pin scroll position so user NEVER jumps to the end of the appended chapter
      window.scrollTo({ top: savedScrollY, behavior: 'instant' });
      void document.body.offsetHeight;
    }

    // Keep DOM memory bounded by pruning oldest chapter if limit exceeded
    pruneOldestChapter();
  };

  window.appendChapterB64 = function (id, b64Title, index, b64Html, b64Scanlator) {
    window.appendChapter({
      id: id,
      title: decodeB64(b64Title),
      index: index,
      htmlContent: decodeB64(b64Html),
      scanlator: decodeB64(b64Scanlator)
    });
  };

  window.prependChapter = function (chapterData) {
    state.isLoadingPrev = false;
    if (!chapterData || !chapterData.id) return;
    if (state.chapters.has(chapterData.id)) return; // Prevent duplicates

    if (state.isPagedMode) {
      const prevTotal = state.totalPages;
      const wrapper = createChapterElement(chapterData);
      elements.container.insertBefore(wrapper, elements.container.firstChild);
      state.chapters.set(chapterData.id, chapterData);

      runUserCustomJs();

      const clientWidth = window.innerWidth;
      const newTotal = Math.max(1, Math.round(elements.container.scrollWidth / clientWidth));
      state.totalPages = newTotal;
      const addedPages = newTotal - prevTotal;

      if (addedPages > 0) {
        if (state.pendingTurnToPrevious) {
          state.pendingTurnToPrevious = false;
          // Land on the last page of the prepended chapter
          state.currentPage = addedPages - 1;
          applyPageTransform();
        } else {
          // Shift currentPage without animation so the user's view remains unchanged
          state.currentPage += addedPages;
          elements.container.style.transition = 'none';
          applyPageTransform();
          void elements.container.offsetHeight;
          elements.container.style.transition = '';
        }
      }
      updateActiveChapterAndProgress();
      hidePagedLoader();
      pruneNewestChapter();
      return;
    }

    // Continuous mode scroll compensation
    const prevScrollHeight = document.documentElement.scrollHeight;
    const prevScrollTop = window.scrollY;

    const wrapper = createChapterElement(chapterData);
    elements.container.insertBefore(wrapper, elements.container.firstChild);
    state.chapters.set(chapterData.id, chapterData);

    runUserCustomJs();

    const newScrollHeight = document.documentElement.scrollHeight;
    const diff = newScrollHeight - prevScrollHeight;

    if (prevScrollTop <= 15) {
      window.scrollTo({ top: Math.max(0, diff - 100), behavior: 'instant' });
    } else {
      window.scrollTo({ top: prevScrollTop + diff, behavior: 'instant' });
    }

    if (state.hasMorePrev) setSentinelState(elements.topSentinel, 'idle', '');
    updateTopSentinelBanner();

    // Keep DOM memory bounded by pruning newest chapter if limit exceeded
    pruneNewestChapter();
  };

  window.prependChapterB64 = function (id, b64Title, index, b64Html, b64Scanlator) {
    window.prependChapter({
      id: id,
      title: decodeB64(b64Title),
      index: index,
      htmlContent: decodeB64(b64Html),
      scanlator: decodeB64(b64Scanlator)
    });
  };

  window.setHasMorePrevious = function (hasMore) {
    state.hasMorePrev = hasMore;
    state.isLoadingPrev = false;
    hidePagedLoader();
    setSentinelState(elements.topSentinel, hasMore ? 'idle' : 'boundary', '');
    updateTopSentinelBanner();
  };

  window.setHasMoreNext = function (hasMore) {
    state.hasMoreNext = hasMore;
    state.isLoadingNext = false;
    document.body.classList.remove('loading-next');
    hidePagedLoader();
    setSentinelState(elements.bottomSentinel, hasMore ? 'idle' : 'boundary', hasMore ? '' : '── End of Novel ──');
  };

  window.setReadingMode = function (mode) {
    const wasPaged = state.isPagedMode;
    state.readingMode = mode || 'CONTINUOUS';
    state.isPagedMode = mode === 'PAGED' || mode === 'PAGED_RTL';
    document.body.classList.toggle('paged-mode', state.isPagedMode);

    if (state.isPagedMode) {
      state.currentPage = 0;
      window.scrollTo({ top: 0, behavior: 'instant' });
      requestAnimationFrame(() => {
        recalculatePages();
        updateActiveChapterAndProgress();
      });
    } else if (wasPaged) {
      elements.container.style.transform = '';
      state.currentPage = 0;
    }
    updateTopSentinelBanner();
  };

  window.applyReaderSettings = function (settings) {
    if (!settings) return;
    const root = document.documentElement;

    if (settings.fontSize) root.style.setProperty('--reader-font-size', settings.fontSize + 'px');
    if (settings.lineHeight) root.style.setProperty('--reader-line-height', settings.lineHeight);
    if (settings.fontFamily) root.style.setProperty('--reader-font-family', `"${settings.fontFamily}", serif`);
    if (settings.paddingH) root.style.setProperty('--reader-padding-h', settings.paddingH + 'px');
    if (settings.textAlign) root.style.setProperty('--reader-text-align', settings.textAlign);
    if (settings.accentColor) root.style.setProperty('--reader-accent-color', settings.accentColor);

    if (settings.theme) {
      document.body.classList.remove('theme-oled', 'theme-dark', 'theme-sepia', 'theme-paper', 'theme-light');
      document.body.classList.add('theme-' + settings.theme.toLowerCase());
    }

    if (settings.readingMode) window.setReadingMode(settings.readingMode);

    const customCss = settings.customCssB64 ? decodeB64(settings.customCssB64) : (settings.customCss || '');
    const customJs = settings.customJsB64 ? decodeB64(settings.customJsB64) : (settings.customJs || '');

    if (typeof customCss === 'string') {
      let cssEl = document.getElementById('user-custom-css');
      if (!cssEl) {
        cssEl = document.createElement('style');
        cssEl.id = 'user-custom-css';
        document.head.appendChild(cssEl);
      }
      cssEl.textContent = customCss;
    }

    if (typeof customJs === 'string') {
      state.customJs = customJs;
      runUserCustomJs();
    }

    if (state.isPagedMode) recalculatePages();
  };

  // ==========================================================================
  // Touch Gestures, Taps & Link Routing
  // ==========================================================================
  let touchStartX = 0;
  let touchStartY = 0;
  let touchStartScrollY = 0;
  let touchStartTime = 0;
  let lastTouchEndTime = 0;

  function handleTap(clientX, clientY) {
    const xRatio = clientX / window.innerWidth;
    const yRatio = clientY != null ? clientY / window.innerHeight : 0.5;
    const b = window.BunoriBridge || bridge;

    if (state.readingMode === 'VERTICAL_TAP') {
      if (yRatio < 0.30) {
        window.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' });
      } else if (yRatio > 0.70) {
        window.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' });
      } else {
        b.onCenterTap();
      }
      return;
    }

    if (state.readingMode === 'PAGED_RTL') {
      if (xRatio < 0.33) {
        pageTurn(1); // Left tap -> Next page in RTL
      } else if (xRatio > 0.67) {
        pageTurn(-1); // Right tap -> Prev page in RTL
      } else {
        b.onCenterTap();
      }
      return;
    }

    if (state.isPagedMode) {
      if (xRatio < 0.33) {
        pageTurn(-1); // Left tap -> Prev page in LTR
      } else if (xRatio > 0.67) {
        pageTurn(1); // Right tap -> Next page in LTR
      } else {
        b.onCenterTap();
      }
    } else {
      if (xRatio < 0.25) {
        window.scrollBy({ top: -window.innerHeight * 0.75, behavior: 'smooth' });
      } else if (xRatio > 0.75) {
        window.scrollBy({ top: window.innerHeight * 0.75, behavior: 'smooth' });
      } else {
        b.onCenterTap();
      }
    }
  }

  document.addEventListener('touchstart', (e) => {
    if (e.touches.length === 1) {
      touchStartX = e.touches[0].clientX;
      touchStartY = e.touches[0].clientY;
      touchStartScrollY = window.scrollY;
      touchStartTime = Date.now();
    }
  }, { passive: true });

  document.addEventListener('touchmove', () => {
    state.userHasScrolled = true;
  }, { passive: true });

  document.addEventListener('touchend', (e) => {
    if (!e.changedTouches || e.changedTouches.length === 0) return;
    const touch = e.changedTouches[0];
    const diffX = touch.clientX - touchStartX;
    const diffY = touch.clientY - touchStartY;
    const absX = Math.abs(diffX);
    const absY = Math.abs(diffY);
    const dt = Date.now() - touchStartTime;

    // Pull down at top of document to load previous chapter (Continuous / Vertical Tap)
    if (!state.isPagedMode && touchStartScrollY <= 15 && window.scrollY <= 15 && diffY > 50 && absY > absX * 1.5) {
      if (state.hasMorePrev && !state.isLoadingPrev) {
        const first = getFirstChapterInfo();
        if (first) {
          state.isLoadingPrev = true;
          setSentinelState(elements.topSentinel, 'loading', 'Loading previous chapter...');
          bridge.onRequestPreviousChapter(first.id);
          return;
        }
      }
    }

    // Horizontal swipe gesture in paged mode
    if (state.isPagedMode && absX > 50 && absX > absY * 1.5) {
      lastTouchEndTime = Date.now();
      if (state.readingMode === 'PAGED_RTL') {
        pageTurn(diffX < 0 ? -1 : 1);
      } else {
        pageTurn(diffX < 0 ? 1 : -1);
      }
      return;
    }

    // Capacitive tap
    if (absX < 35 && absY < 35 && dt < 600) {
      const target = e.target;
      if (target.closest('a, button, input, textarea, select')) return;
      const sel = window.getSelection();
      if (sel && sel.toString().trim().length > 0) return;

      lastTouchEndTime = Date.now();
      handleTap(touch.clientX, touch.clientY);
    }
  }, { passive: true });

  document.addEventListener('click', (e) => {
    if (Date.now() - lastTouchEndTime < 600) return;

    const link = e.target.closest('a');
    if (link) {
      const isPointerDisabled = link.style.pointerEvents === 'none' ||
                                 window.getComputedStyle(link).pointerEvents === 'none' ||
                                 window.getComputedStyle(link).display === 'none';
      const href = link.getAttribute('href');

      if (!href || isPointerDisabled) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }

      e.preventDefault();
      if (href.startsWith('#')) {
        const targetEl = document.querySelector(href);
        if (targetEl) targetEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
      } else {
        (window.BunoriBridge || bridge).onLinkClick(link.href);
      }
      return;
    }

    if (e.target.closest('button, input, textarea, select')) return;
    const selection = window.getSelection();
    if (selection && selection.toString().trim().length > 0) return;

    handleTap(e.clientX, e.clientY);
  });

  // ==========================================================================
  // Continuous Scroll Listener
  // ==========================================================================
  let isScrollScheduled = false;
  window.addEventListener('scroll', () => {
    state.userHasScrolled = true;
    if (state.isPagedMode) return;
    if (!isScrollScheduled) {
      isScrollScheduled = true;
      requestAnimationFrame(() => {
        updateActiveChapterAndProgress();
        isScrollScheduled = false;
      });
    }
  }, { passive: true });

  window.addEventListener('resize', () => {
    if (state.isPagedMode) recalculatePages();
  }, { passive: true });

  document.addEventListener('DOMContentLoaded', () => {
    initObservers();
  });
})();
