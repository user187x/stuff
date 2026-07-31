// 1. Search the browser's cookies for 'banner-text'
const match = document.cookie.match(/(?:^|; )banner-text=([^;]*)/);

// 2. If the auth shim set the cookie, decode it and inject it into the banner
if (match) {
  document.getElementById("subscription-banner").innerText = decodeURIComponent(match[1].replace(/\+/g, " "));
}
