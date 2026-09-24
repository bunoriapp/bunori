const global_this = this;

global_this.console = {
    log: (...args) => __native_log("DEBUG", args.map(String).join(" ")),
    error: (...args) => __native_log("ERROR", args.map(String).join(" ")),
    warn: (...args) => __native_log("WARN", args.map(String).join(" "))
};

global_this.fetchApi = async (url, init = {}) => {
    const raw = await __native_fetch(url, JSON.stringify(init));
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    const headersObj = parsed.headers || {};

    return {
        status: parsed.status || 200,
        statusText: parsed.statusText || "OK",
        ok: (parsed.status >= 200 && parsed.status < 300) || parsed.status === undefined,
        headers: {
            get: (key) => headersObj[key.toLowerCase()] || headersObj[key] || null,
            ...headersObj
        },
        text: async () => parsed.body || "",
        json: async () => JSON.parse(parsed.body || "{}")
    };
};
global_this.fetch = global_this.fetchApi;

function getPlugin() {
    if (global_this.__pluginInstance) return global_this.__pluginInstance;

    if (typeof module !== "undefined" && module.exports) {
        global_this.__pluginInstance = module.exports.default || module.exports;
    } else if (typeof plugin !== "undefined") {
        global_this.__pluginInstance = plugin;
    } else if (typeof defaultPlugin !== "undefined") {
        global_this.__pluginInstance = defaultPlugin;
    }
    return global_this.__pluginInstance;
}

global_this.__bunori_bridge = {
    search: async function (query, page) {
        const plugin = getPlugin();
        if (!plugin) throw new Error("LNReader Plugin not initialized");
        const res = await plugin.searchNovels(query, page);
        return (res || []).map(item => ({
            url: item.url,
            title: item.name,
            coverUrl: item.cover || null,
            author: null
        }));
    },
    getNovelDetails: async function (novelUrl) {
        const plugin = getPlugin();
        if (!plugin) throw new Error("LNReader Plugin not initialized");
        const novel = await plugin.parseNovel(novelUrl);
        return {
            url: novel.url || novelUrl,
            title: novel.name,
            coverUrl: novel.cover || null,
            author: novel.author || null,
            description: novel.summary || null,
            status: novel.status || null,
            genres: Array.isArray(novel.genres) 
                ? novel.genres 
                : (typeof novel.genres === "string" ? novel.genres.split(",").map(g => g.trim()) : []),
            chapters: (novel.chapters || []).map((ch, idx) => ({
                url: ch.url,
                title: ch.name || `Chapter ${idx + 1}`,
                index: ch.chapterNumber !== undefined ? ch.chapterNumber : idx,
                releaseDate: ch.releaseTime || null,
                scanlation: ch.scanlator || ch.group || ch.team || null
            })),
            extra: {}
        };
    },
    getChapterContent: async function (chapterUrl) {
        const plugin = getPlugin();
        if (!plugin) throw new Error("LNReader Plugin not initialized");
        return await plugin.parseChapter(chapterUrl);
    },
    getListings: function () {
        return [
            { id: "popular", name: "Popular" },
            { id: "latest", name: "Latest Updates" }
        ];
    },
    getListingNovels: async function (listingId, page) {
        const plugin = getPlugin();
        if (!plugin) throw new Error("LNReader Plugin not initialized");
        const isLatest = listingId === "latest";
        const res = await plugin.popularNovels(page, { showLatestNovels: isLatest });
        return (res || []).map(item => ({
            url: item.url,
            title: item.name,
            coverUrl: item.cover || null,
            author: null
        }));
    }
};