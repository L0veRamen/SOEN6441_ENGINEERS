/**
 * NotiLytics D2 - WebSocket Client
 *
 * Manages WebSocket connection for real-time news updates.
 * Implements message protocol defined in the group design document.
 *
 * Message Types:
 * Client → Server:
 *   - start_search: Begin new live search
 *   - stop_search: Stop current search
 *   - ping: Keep-alive heartbeat
 *
 * Server → Client:
 *   - initial_results: First 10 articles (immediate)
 *   - append: New articles (live updates)
 *   - status: Operation status
 *   - error: Error messages
 *   - pong: Heartbeat response
 *   - sourceProfile, wordStats, sentiment, readability, sources: Task results
 *
 * @author Group Members
 */

(function () {
    'use strict';

    // ========== STATE MANAGEMENT ==========

    let ws = null;
    let isConnected = false;
    let currentQuery = null;
    let isSearching = false;
    let pingInterval = null;
    let reconnectAttempts = 0;
    const MAX_RECONNECT_ATTEMPTS = 5;
    const RECONNECT_DELAY = 3000; // 3 seconds
    const PING_INTERVAL = 30000; // 30 seconds
    // Articles currently displayed in the live results section
    let liveArticles = [];

    // ========== DOM ELEMENTS ==========

    let elements = {};

    /**
     * Initialize DOM element references
     * @author Group Members
     */
    function initElements() {
        elements = {
            // Controls
            queryInput: document.getElementById('ws-query'),
            sortBySelect: document.getElementById('ws-sortBy'),
            startBtn: document.getElementById('start-search-btn'),
            stopBtn: document.getElementById('stop-search-btn'),

            // Status
            statusIndicator: document.getElementById('ws-status'),
            statusText: document.getElementById('ws-status-text'),

            // Results containers
            resultsContainer: document.getElementById('live-results'),
            searchInfo: document.getElementById('search-info'),
            articlesContainer: document.getElementById('articles-container'),

            // Task panels
            readabilityPanel: document.getElementById('readability-panel'),
            sentimentPanel: document.getElementById('sentiment-panel'),
            sourceProfilePanel: document.getElementById('source-profile-panel'),
            wordStatsPanel: document.getElementById('word-stats-panel'),
            sourcesPanel: document.getElementById('sources-panel'),

            // Add this where the other event listeners are set up
            History: document.getElementById('refresh-history-btn')?.addEventListener('click', function() {
                console.log('[History] Manual refresh requested');
                getHistory(false); // false = show loading indicator
            })
        };
    }

    // ========== WEBSOCKET CONNECTION ==========

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
     * Handle WebSocket connection opened
     * @author Group Members
     */
    function handleOpen() {
        console.log('[WebSocket] Connected');
        isConnected = true;
        reconnectAttempts = 0;
        updateStatus('connected', 'Connected Successfully');

        // Start the ping interval
        startPingInterval();

        // Enable controls
        enableControls();

        // AUTO-REQUEST HISTORY ON CONNECT
        setTimeout(() => {
            getHistory();
        }, 500); // Small delay to ensure the actor is ready
    }

    /**
     * Handle incoming WebSocket message
     * @param {MessageEvent} event WebSocket message event
     * @author Group Members
     */
    function handleMessage(event) {
        try {
            const message = JSON.parse(event.data);
            console.log('[WebSocket] Received:', message.type);

            switch (message.type) {
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
                case 'pong':
                    console.log('[WebSocket] Pong received');
                    break;
                case 'history':  // ← NEW CASE
                    handleHistory(message.data);
                    break;
                // Individual task results
                case 'readability':
                    handleReadabilityResult(message.data);
                    break;
                case 'sentiment':
                    handleSentimentResult(message.data);
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
            console.error('[WebSocket] Failed to parse message:', error);
        }
    }

    /**
     * Request search history from server
     * @param {boolean} silent - If true, don't show loading indicator
     * @author Group Members
     */
    function getHistory(silent = false) {
        if (!isConnected) {
            console.warn('[WebSocket] Cannot get history - not connected');
            return;
        }

        console.log('[WebSocket] Requesting search history');

        // Show loading indicator (unless silent)
        const historyContainer = document.getElementById('history-container');
        if (!silent && historyContainer && historyContainer.children.length > 0) {
            historyContainer.style.opacity = '0.6';
        }

        const message = {
            type: 'get_history'
        };

        ws.send(JSON.stringify(message));
    }

    /**
     * Handle search history response
     * @param {Object} data History data
     * @author Group Members
     */
    function handleHistory(data) {
        console.log('[History] Received history:', data.count, 'searches');

        const historyContainer = document.getElementById('history-container');
        if (!historyContainer) {
            console.warn('[History] No history container found');
            return;
        }

        // Restore opacity (remove loading effect)
        historyContainer.style.opacity = '1';
        historyContainer.style.transition = 'opacity 0.3s';

        // Clear existing history
        historyContainer.innerHTML = '';

        if (data.count === 0) {
            historyContainer.innerHTML = '<p class="no-history">No search history yet</p>';
            return;
        }

        // Create the history list
        const historyList = document.createElement('div');
        historyList.className = 'history-list';

        data.searches.forEach((search, index) => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';

            historyItem.innerHTML = `
            <div class="history-header">
                <span class="history-query">${escapeHtml(search.query)}</span>
                <span class="history-count">${search.totalResults} results</span>
            </div>
            <div class="history-meta">
                <span>Sort: ${escapeHtml(search.sortBy)}</span>
                <span>Time: ${new Date(search.createdAtIso).toLocaleString()}</span>
            </div>
            <button class="history-replay" data-query="${escapeHtml(search.query)}" 
                    data-sortby="${escapeHtml(search.sortBy)}">
                Replay Search
            </button>
        `;

            historyList.appendChild(historyItem);
        });

        historyContainer.appendChild(historyList);

        // Add event listeners to replay buttons
        historyContainer.querySelectorAll('.history-replay').forEach(button => {
            button.addEventListener('click', function() {
                const query = this.getAttribute('data-query');
                const sortBy = this.getAttribute('data-sortby');

                // Set form values
                if (elements.queryInput) elements.queryInput.value = query;
                if (elements.sortBySelect) elements.sortBySelect.value = sortBy;

                // Start search
                startSearch();
            });
        });
    }



    /**
     * Handle WebSocket error
     * @param {Event} event Error event
     * @author Group Members
     */
    function handleError(event) {
        console.error('[WebSocket] Error:', event);
        updateStatus('error', 'Connection error');
    }

    /**
     * Handle WebSocket connection closed
     * @param {CloseEvent} event Close event
     * @author Group Members
     */
    function handleClose(event) {
        console.log('[WebSocket] Closed:', event.code, event.reason);
        isConnected = false;
        isSearching = false;
        stopPingInterval();

        updateStatus('disconnected', 'Disconnected');
        disableControls();

        // Attempt reconnection
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            console.log(`[WebSocket] Reconnect attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}`);
            updateStatus('connecting', `Reconnecting... (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
            setTimeout(initWebSocket, RECONNECT_DELAY);
        } else {
            updateStatus('error', 'Connection lost. Please refresh page.');
        }
    }

    // ========== MESSAGE HANDLERS ==========

    /**
     * Handle initial search results
     * @param {Object} data Initial results data
     * @author Group Members
     */
    function handleInitialResults(data) {
        console.log('[Results] Initial results received:', data.articles.length);

        // Track the current batch of live articles (for per-article readability)
        liveArticles = data.articles.slice();

        // Update search info
        updateSearchInfo(data);

        // Clear previous results
        elements.articlesContainer.innerHTML = '';

        // Display articles
        data.articles.forEach(article => {
            appendArticle(article, false); // false = not new
        });

        // Show the result container
        elements.resultsContainer.style.display = 'block';

        // AUTO-REFRESH HISTORY AFTER SEARCH COMPLETES
        console.log('[History] Auto-refreshing history after search completion');
        setTimeout(() => {
            getHistory();
        }, 500); // Small delay to ensure backend has processed

        // Let the separate task messages populate panels,
        // but we can also bootstrap readability using the initial payload.
        if (data.readability && data.articleReadability) {
            const readabilityPayload = {
                gradeLevel: data.readability.gradeLevel,
                readingEase: data.readability.readingEase,
                interpretation: getReadingEaseInterpretation(data.readability.readingEase),
                articleCount: data.articleReadability.length,
                isValid: data.readability.gradeLevel > 0,
                articleScores: data.articleReadability.map(s => ({
                    gradeLevel: s.gradeLevel,
                    readingEase: s.readingEase,
                    interpretation: getReadingEaseInterpretation(s.readingEase),
                    isValid: s.gradeLevel > 0
                }))
            };
            handleReadabilityResult(readabilityPayload);
        }

        if (data.sentiment) {
            handleSentimentResult(data.sentiment);
        }
    }

    /**
     * Handle appended new articles
     * @param {Object} data Append data
     * @author Group Members
     */
    function handleAppendResults(data) {
        console.log('[Results] New articles appended:', data.count);

        if (data.articles && data.articles.length > 0) {
            data.articles.forEach(article => {
                liveArticles.push(article);          // track for readability
                appendArticle(article, true);        // true = new article
            });

            // Show notification
            showNotification(`${data.count} new article(s) added`);
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
     * Handle readability analysis result
     * @param {Object} data Readability data
     * Expected shape (from ReadabilityActor OR built in handleInitialResults):
     *  {
     *    gradeLevel: number,
     *    readingEase: number,
     *    interpretation: string,
     *    articleCount: number,
     *    isValid: boolean,
     *    articleScores?: [{ gradeLevel, readingEase, interpretation, isValid }]
     *  }
     * @author Group Members
     */
    function handleReadabilityResult(data) {
        if (!elements.readabilityPanel) return;

        const panel = elements.readabilityPanel;
        const summaryEl = document.getElementById('readability-summary');
        let breakdownEl = document.getElementById('readability-articles');

        if (!summaryEl) return;

        // Make panel visible
        panel.style.display = 'block';

        // --- Overall summary ---

        if (!data || data.isValid === false || !data.articleCount) {
            summaryEl.innerHTML = `
            <p>No valid descriptions found to calculate readability.</p>
        `;
            if (breakdownEl) breakdownEl.innerHTML = '';
            return;
        }

        const overallInterpretation =
            data.interpretation || getReadingEaseInterpretation(data.readingEase);

        summaryEl.innerHTML = `
        <div class="task-result-content">
            <p><strong>Average Grade Level:</strong> ${data.gradeLevel.toFixed(1)}</p>
            <p><strong>Average Reading Ease:</strong> ${data.readingEase.toFixed(1)}</p>
            <p><strong>Interpretation:</strong> ${overallInterpretation}</p>
            <h3 class="metric-footnote">
                Based on ${data.articleCount} article${data.articleCount === 1 ? '' : 's'}.
            </h3>
        </div>
    `;

        // --- Per-article breakdown ---

        const articleScores = data.articleScores || [];
        if (!breakdownEl) {
            breakdownEl = document.createElement('div');
            breakdownEl.id = 'readability-articles';
            breakdownEl.className = 'readability-articles';
            summaryEl.insertAdjacentElement('afterend', breakdownEl);
        }

        if (!articleScores.length) {
            breakdownEl.innerHTML = '';
            return;
        }

        // If we don't have titles for some reason, still show the scores
        const count = liveArticles.length
            ? Math.min(articleScores.length, liveArticles.length)
            : articleScores.length;

        const rows = [];
        let visibleIndex = 1;

        for (let i = 0; i < count; i++) {
            const score = articleScores[i];
            if (!score || score.isValid === false) continue;

            const article = liveArticles[i] || {};
            const title = article.title ? escapeHtml(article.title) : `Article ${visibleIndex}`;
            const interp =
                score.interpretation || getReadingEaseInterpretation(score.readingEase);

            rows.push(`
                <div class="readability-row">
                    <span class="readability-row-index">${visibleIndex}.</span>
                    <div class="readability-row-main">
                        <div class="readability-item-title">${title}</div>
                        <div class="readability-item-meta">
                            Grade ${score.gradeLevel.toFixed(1)},
                            Ease ${score.readingEase.toFixed(1)}
                            <span class="readability-interpretation">(${interp})</span>
                        </div>
                    </div>
                </div>
            `);
            visibleIndex++;
        }

        if (rows.length) {
            breakdownEl.innerHTML = `
                <h4>Per-Article Readability</h4>
                <div class="readability-article-list">
                    ${rows.join('')}
                </div>
            `;
        } else {
            breakdownEl.innerHTML = '';
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
     * Handle sentiment analysis result
     * @param {Object} data Sentiment data
     * @author Group Members
     */
    function handleSentimentResult(data) {
        if (!elements.sentimentPanel) return;

        elements.sentimentPanel.innerHTML = `
            <h4>💬 Sentiment Analysis</h4>
            <div class="task-result-content">
                <p><strong>Overall Sentiment:</strong> ${data.sentiment}</p>
                <p><strong>Description:</strong> ${data.description}</p>
            </div>
        `;
        elements.sentimentPanel.style.display = 'block';
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
            <h4>🌐 Source Profile: ${data.sourceName}</h4>
            <div class="task-result-content">
                <p><strong>Total Articles:</strong> ${data.totalArticles}</p>
                ${profile ? `
                    <p><strong>Name:</strong> ${profile.name}</p>
                    <p><strong>Description:</strong> ${profile.description || 'N/A'}</p>
                    <p><strong>Category:</strong> ${profile.category || 'N/A'}</p>
                    <p><strong>Language:</strong> ${profile.language || 'N/A'}</p>
                    <p><strong>Country:</strong> ${profile.country || 'N/A'}</p>
                    ${profile.url ? `<p><strong>Website:</strong> <a href="${profile.url}" target="_blank">${profile.url}</a></p>` : ''}
                ` : '<p>No profile data available</p>'}
            </div>
        `;
        elements.sourceProfilePanel.style.display = 'block';
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
                <p><strong>Query:</strong> ${data.query}</p>
                <p><strong>Total Articles:</strong> ${data.totalArticles}</p>
                <p><strong>Total Words:</strong> ${data.totalWords}</p>
                <p><strong>Unique Words:</strong> ${data.uniqueWords}</p>
                <h5>Top 10 Words:</h5>
                <ul>
                    ${topWords.map(w => `<li>${w.word}: ${w.count}</li>`).join('')}
                </ul>
            </div>
        `;
        elements.wordStatsPanel.style.display = 'block';
    }

    /**
     * Handle news sources result
     * @param {Object} data Sources data
     * @author Group Members
     */
    function handleSourcesResult(data) {
        if (!elements.sourcesPanel) return;

        const sources = data.sources.slice(0, 10);

        elements.sourcesPanel.innerHTML = `
            <h4>📡 News Sources</h4>
            <div class="task-result-content">
                <p><strong>Total Sources:</strong> ${data.count}</p>
                <h5>Top 10 Sources:</h5>
                <ul>
                    ${sources.map(s => `
                        <li>
                            <strong>${s.name}</strong> (${s.id})
                            <br>Category: ${s.category}, Language: ${s.language}, Country: ${s.country}
                        </li>
                    `).join('')}
                </ul>
            </div>
        `;
        elements.sourcesPanel.style.display = 'block';
    }

    // ========== UI UPDATES ==========

    /**
     * Update connection status indicator
     * @param {string} status Status type (connecting|connected|disconnected|error)
     * @param {string} text Status text
     * @author Group Members
     */
    function updateStatus(status, text) {
        if (!elements.statusIndicator || !elements.statusText) return;

        elements.statusIndicator.className = 'status-indicator status-' + status;
        elements.statusText.textContent = text;
    }

    /**
     * Update search information display
     * @param {Object} data Search data
     * @author Group Members
     */
    function updateSearchInfo(data) {
        if (!elements.searchInfo) return;

        elements.searchInfo.innerHTML = `
            <h3>${data.query}</h3>
            <div class="search-meta">
                <span><strong>${data.totalResults}</strong> total results</span>
                <span>Sorted by: <strong>${data.sortBy}</strong></span>
                <span>${new Date(data.timestamp).toLocaleString()}</span>
            </div>
        `;
    }

    /**
     * Append article to results container
     * @param {Object} article Article data
     * @param {boolean} isNew Whether article is newly added
     * @author Group Members
     */
    function appendArticle(article, isNew) {
        const articleDiv = document.createElement('div');
        articleDiv.className = 'article' + (isNew ? ' new-article' : '');

        articleDiv.innerHTML = `
            ${isNew ? '<span class="new-badge">NEW</span>' : ''}
            <div class="article-title">
                ${article.url ?
            `<a href="${escapeHtml(article.url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(article.title)}</a>` :
            escapeHtml(article.title)
        }
            </div>
            <div class="article-meta">
                ${article.sourceName ? `Source: <strong>${escapeHtml(article.sourceName)}</strong>` : ''}
                ${article.publishedAt ? ` | Published: ${new Date(article.publishedAt).toLocaleString()}` : ''}
            </div>
            ${article.description ?
            `<div class="article-description">${escapeHtml(article.description)}</div>` :
            ''
        }
        `;

        elements.articlesContainer.appendChild(articleDiv);

        // Scroll new articles into view smoothly
        if (isNew) {
            articleDiv.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
    }

    /**
     * Show notification toast
     * @param {string} message Notification message
     * @author Group Members
     */
    function showNotification(message) {
        // Simple notification - you can enhance this with a proper toast library
        const notification = document.createElement('div');
        notification.className = 'notification';
        notification.textContent = message;
        document.body.appendChild(notification);

        setTimeout(() => {
            notification.classList.add('show');
        }, 10);

        setTimeout(() => {
            notification.classList.remove('show');
            setTimeout(() => notification.remove(), 300);
        }, 3000);
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} text Text to escape
     * @returns {string} Escaped text
     * @author Group Members
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ========== CONTROLS ==========

    /**
     * Enable search controls
     * @author Group Members
     */
    function enableControls() {
        if (elements.queryInput) elements.queryInput.disabled = false;
        if (elements.sortBySelect) elements.sortBySelect.disabled = false;
        if (elements.startBtn) {
            elements.startBtn.disabled = false;
            elements.startBtn.textContent = 'Start Live Search';
        }
    }

    /**
     * Disable search controls
     * @author Group Members
     */
    function disableControls() {
        if (elements.queryInput) elements.queryInput.disabled = true;
        if (elements.sortBySelect) elements.sortBySelect.disabled = true;
        if (elements.startBtn) elements.startBtn.disabled = true;
        if (elements.stopBtn) elements.stopBtn.disabled = true;
    }

    /**
     * Start new search
     * @author Group Members
     */
    function startSearch() {
        liveArticles = [];           // reset for new search
        elements.articlesContainer.innerHTML = '';  // clear old live results

        if (!isConnected) {
            alert('WebSocket not connected. Please wait or refresh the page.');
            return;
        }

        const query = elements.queryInput.value.trim();
        if (!query) {
            alert('Please enter a search query');
            elements.queryInput.focus();
            return;
        }

        const sortBy = elements.sortBySelect.value;

        console.log('[Search] Starting:', query, sortBy);

        // Send start_search message
        sendMessage({
            type: 'start_search',
            query: query,
            sortBy: sortBy
        });

        // Update state
        currentQuery = query;
        isSearching = true;

        // Update UI
        elements.startBtn.disabled = true;
        elements.stopBtn.disabled = false;
        updateStatus('searching', `Searching for "${query}"...`);

        // Hide task panels until new results arrive
        hideTaskPanels();
    }

    /**
     * Stop current search
     * @author Group Members
     */
    function stopSearch() {
        if (!isConnected || !isSearching) return;

        console.log('[Search] Stopping');

        // Send stop_search message
        sendMessage({
            type: 'stop_search'
        });

        // Update state
        isSearching = false;

        // Update UI
        elements.startBtn.disabled = false;
        elements.stopBtn.disabled = true;
        updateStatus('connected', 'Search stopped');
    }

    /**
     * Hide all task panels
     * @author Group Members
     */
    function hideTaskPanels() {
        [
            elements.readabilityPanel,
            elements.sentimentPanel,
            elements.sourceProfilePanel,
            elements.wordStatsPanel,
            elements.sourcesPanel
        ].forEach(panel => {
            if (panel) panel.style.display = 'none';
        });
    }

    /**
     * Send the message to server
     * @param {Object} message Message object
     * @author Group Members
     */
    function sendMessage(message) {
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            console.error('[WebSocket] Cannot send message - not connected');
            return;
        }

        try {
            ws.send(JSON.stringify(message));
            console.log('[WebSocket] Sent:', message.type);
        } catch (error) {
            console.error('[WebSocket] Failed to send message:', error);
        }
    }

    // ========== PING/PONG ==========

    /**
     * Start ping interval
     * @author Group Members
     */
    function startPingInterval() {
        stopPingInterval(); // Clear any existing interval

        pingInterval = setInterval(() => {
            if (isConnected) {
                sendMessage({type: 'ping'});
            }
        }, PING_INTERVAL);
    }

    /**
     * Stop ping interval
     * @author Group Members
     */
    function stopPingInterval() {
        if (pingInterval) {
            clearInterval(pingInterval);
            pingInterval = null;
        }
    }

    // ========== EVENT LISTENERS ==========

    /**
     * Initialize event listeners
     * @author Group Members
     */
    function initEventListeners() {
        // Start search button
        if (elements.startBtn) {
            elements.startBtn.addEventListener('click', startSearch);
        }

        // Stop search button
        if (elements.stopBtn) {
            elements.stopBtn.addEventListener('click', stopSearch);
        }

        // Enter key in query input
        if (elements.queryInput) {
            elements.queryInput.addEventListener('keypress', (e) => {
                if (e.key === 'Enter' && !isSearching) {
                    startSearch();
                }
            });
        }

        // Close connection on page unloading
        window.addEventListener('beforeunload', () => {
            if (ws) {
                ws.close();
            }
        });
    }

    // ========== INITIALIZATION ==========

    /**
     * Initialize WebSocket client
     * @author Group Members
     */
    function init() {
        console.log('[WebSocket] Initializing client');

        // Initialize DOM elements
        initElements();

        // Initialize event listeners
        initEventListeners();

        // Connect WebSocket
        initWebSocket();
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();