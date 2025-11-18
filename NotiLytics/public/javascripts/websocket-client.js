/**
 * WebSocket Client for D2 Live Search
 * Displays readability and sentiment inline with search results
 *
 * @author Group Members
 */
(function() {
    'use strict';

    let ws;
    let liveArticles = [];
    let currentReadability = null;
    let currentSentiment = null;
    let currentArticleReadability = [];

    const elements = {
        wsQuery: document.getElementById('ws-query'),
        wsSortBy: document.getElementById('ws-sortBy'),
        startBtn: document.getElementById('start-search-btn'),
        stopBtn: document.getElementById('stop-search-btn'),
        wsStatus: document.getElementById('ws-status'),
        wsStatusText: document.getElementById('ws-status-text'),
        liveResults: document.getElementById('live-results'),
        searchInfo: document.getElementById('search-info'),
        articlesContainer: document.getElementById('articles-container'),
        historyContainer: document.getElementById('history-container'),
        refreshHistoryBtn: document.getElementById('refresh-history-btn'),
        sourceProfilePanel: document.getElementById('source-profile-panel'),
        wordStatsPanel: document.getElementById('word-stats-panel'),
        sourcesPanel: document.getElementById('sources-panel')
    };

    /**
     * Initialize WebSocket connection
     * @author Group Members
     */
    function initWebSocket() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;

        console.log('[WebSocket] Connecting to:', wsUrl);
        updateStatus('connecting', 'Connecting...');

        ws = new WebSocket(wsUrl);

        ws.onopen = handleOpen;
        ws.onmessage = handleMessage;
        ws.onerror = handleError;
        ws.onclose = handleClose;
    }

    /**
     * Handle WebSocket open event
     * @author Group Members
     */
    function handleOpen() {
        console.log('[WebSocket] Connected');
        updateStatus('connected', 'Connected');

        // Enable controls
        elements.wsQuery.disabled = false;
        elements.wsSortBy.disabled = false;
        elements.startBtn.disabled = false;

        // Request initial history
        sendMessage({ type: 'get_history' });
    }

    /**
     * Handle incoming WebSocket messages
     * @param {MessageEvent} event WebSocket message event
     * @author Group Members
     */
    function handleMessage(event) {
        try {
            const message = JSON.parse(event.data);
            console.log('[WebSocket] Message received:', message.type);

            switch (message.type) {
                case 'pong':
                    console.log('[WebSocket] Pong received');
                    break;
                case 'initial_results':
                    handleInitialResults(message.data);
                    break;
                case 'append':
                    handleAppendResults(message.data);
                    break;
                case 'status':
                    handleStatus(message.data);
                    break;
                case 'error':
                    handleErrorMessage(message.data);
                    break;
                case 'history':
                    handleHistoryUpdate(message.data);
                    break;
                case 'readability':
                    handleReadabilityUpdate(message.data);
                    break;
                case 'sentiment':
                    handleSentimentUpdate(message.data);
                    break;
                case 'sourceProfile':
                    handleSourceProfileResult(message.data);
                    break;
                case 'wordStats':
                    handleWordStatsResult(message.data);
                    break;
                case 'sources':
                    handleSourcesResult(message.data);
                    break;
                default:
                    console.warn('[WebSocket] Unknown message type:', message.type);
            }
        } catch (error) {
            console.error('[WebSocket] Error parsing message:', error);
        }
    }

    /**
     * Handle WebSocket error
     * @param {Event} error WebSocket error event
     * @author Group Members
     */
    function handleError(error) {
        console.error('[WebSocket] Error:', error);
        updateStatus('error', 'Connection error');
    }

    /**
     * Handle WebSocket close event
     * @param {CloseEvent} event WebSocket close event
     * @author Group Members
     */
    function handleClose(event) {
        console.log('[WebSocket] Closed:', event.code, event.reason);
        updateStatus('disconnected', 'Disconnected');

        // Disable controls
        elements.wsQuery.disabled = true;
        elements.wsSortBy.disabled = true;
        elements.startBtn.disabled = true;
        elements.stopBtn.disabled = true;

        // Attempt reconnection after 3 seconds
        setTimeout(() => {
            console.log('[WebSocket] Attempting reconnection...');
            initWebSocket();
        }, 3000);
    }

    /**
     * Send a message through WebSocket
     * @param {Object} message Message object to send
     * @author Group Members
     */
    function sendMessage(message) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(message));
            console.log('[WebSocket] Message sent:', message.type);
        } else {
            console.error('[WebSocket] Cannot send message - not connected');
        }
    }

    /**
     * Update connection status indicator
     * @param {string} status Status class (connecting/connected/disconnected/error/searching)
     * @param {string} text Status text
     * @author Group Members
     */
    function updateStatus(status, text) {
        elements.wsStatus.className = `status-indicator status-${status}`;
        elements.wsStatusText.textContent = text;
    }

    /**
     * Start live search
     * @author Group Members
     */
    function startSearch() {
        const query = elements.wsQuery.value.trim();
        const sortBy = elements.wsSortBy.value;

        if (!query) {
            alert('Please enter a search query');
            return;
        }

        console.log('[Search] Starting:', query, sortBy);
        updateStatus('searching', `Searching for "${query}"...`);

        // Clear previous results
        liveArticles = [];
        currentReadability = null;
        currentSentiment = null;
        currentArticleReadability = [];
        elements.articlesContainer.innerHTML = '';
        elements.searchInfo.innerHTML = '';

        // Show results container
        elements.liveResults.classList.add('show');

        // Send start search message
        sendMessage({
            type: 'start_search',
            query: query,
            sortBy: sortBy
        });

        // Update button states
        elements.startBtn.disabled = true;
        elements.stopBtn.disabled = false;
        elements.wsQuery.disabled = true;
        elements.wsSortBy.disabled = true;
    }

    /**
     * Stop live search
     * @author Group Members
     */
    function stopSearch() {
        console.log('[Search] Stopping');
        sendMessage({ type: 'stop_search' });

        updateStatus('connected', 'Connected');

        // Update button states
        elements.startBtn.disabled = false;
        elements.stopBtn.disabled = true;
        elements.wsQuery.disabled = false;
        elements.wsSortBy.disabled = false;
    }

    /**
     * Handle initial search results - INLINE ANALYTICS VERSION
     * Displays readability and sentiment inline with search metadata
     *
     * @param {Object} data Initial results data
     * @author Group Members
     */
    function handleInitialResults(data) {
        console.log('[Results] Initial results received:', data.count);

        // Store analytics data
        currentReadability = data.readability;
        currentSentiment = data.sentiment;
        currentArticleReadability = data.articleReadability || [];

        // Build search info with INLINE analytics
        updateSearchInfo(data);

        // Clear and populate articles
        elements.articlesContainer.innerHTML = '';
        liveArticles = [];

        if (data.articles && data.articles.length > 0) {
            data.articles.forEach((article, index) => {
                liveArticles.push(article);
                const articleReadability = currentArticleReadability[index];
                appendArticle(article, false, articleReadability);
            });
        }
    }

    /**
     * Update search info section with inline analytics
     * @param {Object} data Search data with analytics
     * @author Chen Qian
     */
    function updateSearchInfo(data) {
        const query = escapeHtml(data.query);
        const totalResults = data.totalResults || 0;
        const sortBy = data.sortBy || 'publishedAt';
        const count = data.count || 0;

        // Build analytics inline spans
        let analyticsHtml = '';

        // Readability analytics
        if (currentReadability && currentReadability.gradeLevel > 0) {
            const interpretation = getReadingEaseInterpretation(currentReadability.readingEase);
            analyticsHtml += `
                <span class="analytics-inline">
                    📖 Grade Level: <span class="analytics-value">${currentReadability.gradeLevel.toFixed(1)}</span>
                </span>
                <span class="analytics-inline">
                    📊 Reading Ease: <span class="analytics-value">${currentReadability.readingEase.toFixed(1)}</span>
                </span>
                <span class="analytics-inline">
                    📝 Interpretation: <span class="analytics-value">${interpretation}</span>
                </span>
            `;
        }

        // Sentiment analytics
        if (currentSentiment && currentSentiment.sentiment) {
            analyticsHtml += `
                <span class="analytics-inline sentiment">
                    💬 Sentiment: <span class="analytics-value">${currentSentiment.description || currentSentiment.sentiment}</span>
                </span>
            `;
        }

        // Update the search info section
        elements.searchInfo.innerHTML = `
            <h3>Search Results: "${query}"</h3>
            <div class="search-meta">
                <span>📊 <strong>${totalResults}</strong> total results</span>
                <span>🔄 Sorted by: <strong>${sortBy}</strong></span>
                <span>⏱️ <strong>${count}</strong> articles displayed</span>
                ${analyticsHtml}
            </div>
        `;
    }

    /**
     * Handle appended new articles
     * @param {Object} data Append data
     * @author Group Members
     */
    function handleAppendResults(data) {
        console.log('[Results] New articles appended:', data.count);

        if (data.articles && data.articles.length > 0) {
            data.articles.forEach((article, index) => {
                const articleIndex = liveArticles.length;
                liveArticles.push(article);

                // Get readability for this new article if available
                const articleReadability = data.articleReadability
                    ? data.articleReadability[index]
                    : null;

                appendArticle(article, true, articleReadability);
            });

            showNotification(`${data.count} new article(s) added`);
        }
    }

    /**
     * Append article to the display with inline readability
     * @param {Object} article Article data
     * @param {boolean} isNew Whether this is a newly appended article
     * @param {Object} readability Per-article readability scores
     * @author Group Members
     */
    function appendArticle(article, isNew, readability) {
        const articleEl = document.createElement('div');
        articleEl.className = 'article' + (isNew ? ' new-article' : '');

        // Build readability inline display
        let readabilityHtml = '';
        if (readability && readability.gradeLevel > 0) {
            readabilityHtml = `
                <span class="article-readability-inline">
                    📚 Grade: <strong>${readability.gradeLevel.toFixed(1)}</strong>, 
                    Ease: <strong>${readability.readingEase.toFixed(1)}</strong>
                </span>
            `;
        }

        articleEl.innerHTML = `
            ${isNew ? '<div class="new-badge">NEW</div>' : ''}
            <div class="article-title">
                ${article.url ?
            `<a href="${escapeHtml(article.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(article.title)}</a>` :
            escapeHtml(article.title)
        }
            </div>
            <div class="article-meta">
                ${article.sourceName ? `Source: ${escapeHtml(article.sourceName)}` : ''}
                ${article.publishedAt ? ` | Published: ${escapeHtml(article.publishedAt)}` : ''}
                ${readabilityHtml}
            </div>
            ${article.description ? `<div class="article-description">${escapeHtml(article.description)}</div>` : ''}
        `;

        elements.articlesContainer.appendChild(articleEl);

        // Scroll to new article if it's new
        if (isNew) {
            setTimeout(() => {
                articleEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            }, 100);
        }
    }

    /**
     * Show notification
     * @param {string} message Notification message
     * @author Group Members
     */
    function showNotification(message) {
        console.log('[Notification]', message);

        const metaDiv = elements.searchInfo.querySelector('.search-meta');
        if (metaDiv) {
            const badge = document.createElement('span');
            badge.style.cssText = 'background: #4caf50; color: white; padding: 0.25rem 0.5rem; border-radius: 3px;';
            badge.textContent = message;
            metaDiv.appendChild(badge);

            setTimeout(() => badge.remove(), 3000);
        }
    }

    /**
     * Handle status message
     * @param {Object} data Status data
     * @author Group Members
     */
    function handleStatus(data) {
        console.log('[Status]', data.message);
        updateStatus('connected', data.message);
    }

    /**
     * Handle error message
     * @param {Object} data Error data
     * @author Group Members
     */
    function handleErrorMessage(data) {
        console.error('[Error]', data.message);
        alert('Error: ' + data.message);
        updateStatus('error', data.message);
    }

    /**
     * Handle history update
     * @param {Object} data History data
     * @author Group Members
     */
    function handleHistoryUpdate(data) {
        const searches = data.searches || [];
        console.log('[History] Received', searches.length, 'searches');

        if (searches.length === 0) {
            elements.historyContainer.innerHTML = '<p class="no-results">No search history yet.</p>';
            return;
        }

        elements.historyContainer.innerHTML = searches.map((search, index) => `
            <div class="history-item" style="padding: 1rem; border-bottom: 1px solid #e0e0e0; cursor: pointer;"
                 onclick="replaySearch('${escapeHtml(search.query)}', '${search.sortBy}')">
                <h4 style="margin: 0 0 0.5rem 0; color: #667eea;">
                    ${index + 1}. ${escapeHtml(search.query)}
                </h4>
                <div style="font-size: 0.9rem; color: #666;">
                    <span>📊 ${search.totalResults} results</span>
                    <span style="margin-left: 1rem;">🔄 ${search.sortBy}</span>
                    <span style="margin-left: 1rem;">⏱️ ${search.createdAt}</span>
                </div>
            </div>
        `).join('');
    }

    /**
     * Replay a search from history
     * @param {string} query Search query
     * @param {string} sortBy Sort option
     * @author Group Members
     */
    window.replaySearch = function(query, sortBy) {
        elements.wsQuery.value = query;
        elements.wsSortBy.value = sortBy;
        startSearch();
    };

    /**
     * Handle readability update (separate message)
     * Updates the inline analytics display
     * @param {Object} data Readability data
     * @author Chen Qian
     */
    function handleReadabilityUpdate(data) {
        if (data && data.gradeLevel > 0) {
            currentReadability = data;
            currentArticleReadability = data.articleScores || [];

            // Rebuild search info with updated readability
            const currentData = {
                query: elements.wsQuery.value,
                totalResults: liveArticles.length,
                sortBy: elements.wsSortBy.value,
                count: liveArticles.length
            };
            updateSearchInfo(currentData);
        }
    }

    /**
     * Handle sentiment update (separate message)
     * Updates the inline analytics display
     * @param {Object} data Sentiment data
     * @author Group Members
     */
    function handleSentimentUpdate(data) {
        if (data && data.sentiment) {
            currentSentiment = data;

            // Rebuild search info with updated sentiment
            const currentData = {
                query: elements.wsQuery.value,
                totalResults: liveArticles.length,
                sortBy: elements.wsSortBy.value,
                count: liveArticles.length
            };
            updateSearchInfo(currentData);
        }
    }

    /**
     * Get interpretation of reading ease score
     * @param {number} readingEase Reading ease score (0-100)
     * @returns {string} Interpretation text
     * @author Chen Qian
     */
    function getReadingEaseInterpretation(readingEase) {
        if (readingEase >= 90) return "Very Easy";
        if (readingEase >= 80) return "Easy";
        if (readingEase >= 70) return "Fairly Easy";
        if (readingEase >= 60) return "Standard";
        if (readingEase >= 50) return "Fairly Difficult";
        if (readingEase >= 30) return "Difficult";
        return "Very Difficult";
    }

    /**
     * Handle source profile result
     * @param {Object} data Source profile data
     * @author Group Members
     */
    function handleSourceProfileResult(data) {
        if (!elements.sourceProfilePanel) return;

        const profile = data.profile;
        elements.sourceProfilePanel.innerHTML = `
            <h4>🌐 Source Profile: ${escapeHtml(data.sourceName)}</h4>
            <div class="task-result-content">
                <p><strong>Total Articles:</strong> ${data.totalArticles}</p>
                ${profile ? `
                    <p><strong>Name:</strong> ${escapeHtml(profile.name)}</p>
                    <p><strong>Description:</strong> ${escapeHtml(profile.description || 'N/A')}</p>
                    <p><strong>Category:</strong> ${escapeHtml(profile.category || 'N/A')}</p>
                    <p><strong>Language:</strong> ${profile.language || 'N/A'}</p>
                    <p><strong>Country:</strong> ${profile.country || 'N/A'}</p>
                    ${profile.url ? `<p><strong>Website:</strong> <a href="${escapeHtml(profile.url)}" target="_blank">${escapeHtml(profile.url)}</a></p>` : ''}
                ` : '<p>No profile data available</p>'}
            </div>
        `;
        elements.sourceProfilePanel.classList.add('show');
    }

    /**
     * Handle word statistics result
     * @param {Object} data Word stats data
     * @author Group Members
     */
    function handleWordStatsResult(data) {
        if (!elements.wordStatsPanel) return;

        const topWords = data.wordFrequencies.slice(0, 10);

        elements.wordStatsPanel.innerHTML = `
            <h4>📊 Word Statistics</h4>
            <div class="task-result-content">
                <p><strong>Query:</strong> ${escapeHtml(data.query)}</p>
                <p><strong>Total Articles:</strong> ${data.totalArticles}</p>
                <p><strong>Total Words:</strong> ${data.totalWords}</p>
                <p><strong>Unique Words:</strong> ${data.uniqueWords}</p>
                <h5>Top 10 Words:</h5>
                <ul>
                    ${topWords.map(w => `<li>${escapeHtml(w.word)}: ${w.count}</li>`).join('')}
                </ul>
            </div>
        `;
        elements.wordStatsPanel.classList.add('show');
    }

    /**
     * Handle sources result
     * @param {Object} data Sources data
     * @author Group Members
     */
    function handleSourcesResult(data) {
        if (!elements.sourcesPanel) return;

        const sources = data.sources || [];

        elements.sourcesPanel.innerHTML = `
            <h4>📰 News Sources</h4>
            <div class="task-result-content">
                <p><strong>Total Sources:</strong> ${data.count}</p>
                <h5>Available Sources:</h5>
                <ul>
                    ${sources.slice(0, 10).map(s => `
                        <li>
                            <strong>${escapeHtml(s.name)}</strong><br>
                            ${s.description ? escapeHtml(s.description) : 'No description'}
                        </li>
                    `).join('')}
                </ul>
                ${sources.length > 10 ? `<p><em>...and ${sources.length - 10} more</em></p>` : ''}
            </div>
        `;
        elements.sourcesPanel.classList.add('show');
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} text Text to escape
     * @returns {string} Escaped text
     * @author Group Members
     */
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ============================================
    // EVENT LISTENERS
    // ============================================

    elements.startBtn.addEventListener('click', startSearch);
    elements.stopBtn.addEventListener('click', stopSearch);

    elements.wsQuery.addEventListener('keypress', (e) => {
        if (e.key === 'Enter' && !elements.startBtn.disabled) {
            startSearch();
        }
    });

    elements.refreshHistoryBtn.addEventListener('click', () => {
        console.log('[History] Requesting refresh');
        sendMessage({ type: 'get_history' });
    });

    // ============================================
    // INITIALIZATION
    // ============================================

    // Start WebSocket connection on page load
    initWebSocket();

    console.log('[App] WebSocket client initialized - INLINE ANALYTICS MODE');

})();