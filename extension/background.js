const DEFAULT_SERVER = "http://127.0.0.1:18080";

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "jobtrack-add",
    title: "加入投递台账",
    contexts: ["page", "link", "selection"]
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId !== "jobtrack-add") return;
  const link = info.linkUrl || info.pageUrl || "";
  const title = info.selectionText || (tab && tab.title) || "";
  const url =
    chrome.runtime.getURL("popup.html") +
    `?link=${encodeURIComponent(link)}&title=${encodeURIComponent(title)}`;
  chrome.windows.create({
    url,
    type: "popup",
    width: 400,
    height: 640
  });
});
