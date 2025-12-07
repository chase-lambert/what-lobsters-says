# What Lobsters Says

A browser extension that shows you if the page you're viewing has been discussed on [Lobsters](https://lobste.rs). Built with ClojureScript.

Inspired by [What HN Says](https://github.com/pinoceniccola/what-hn-says-webext) by pinoceniccola.

<img width="2482" height="1321" alt="image" src="https://github.com/user-attachments/assets/58568160-0c70-4150-84f3-356969a2bca2" />


## Building

Requires Java (for ClojureScript compiler) and either npm or bun.

```bash
# Install dependencies
bun install  # or: npm install

# Development (watch mode with hot reload)
bun run dev  # or: npm run dev

# Production build
bun run build  # or: npm run build
```

## Installing in Browser

**Chrome/Edge/Brave:**
1. Go to `chrome://extensions/`
2. Enable "Developer mode"
3. Click "Load unpacked" and select the `public/` folder

## How It Works

Click the extension icon on any page. It searches Lobsters for discussions about that URL - first checking domain-specific history, then falling back to recent hottest/newest/active stories. Shows up to 4 matching discussions with scores, comment counts, and direct links.

## License

MIT
