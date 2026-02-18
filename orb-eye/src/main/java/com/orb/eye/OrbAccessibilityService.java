package com.orb.eye;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressLint("AccessibilityPolicy")
public class OrbAccessibilityService extends AccessibilityService {
    private static final String TAG = "OrbEye";
    private static final int PORT = 7333;
    private static final int MAX_NOTIFICATIONS = 50;

    private ServerSocket serverSocket;
    private Thread serverThread;

    // === Feature 1: Notification buffer ===
    private final CopyOnWriteArrayList<JSONObject> notificationBuffer = new CopyOnWriteArrayList<>();

    // === Feature 3: Wait for UI change ===
    private volatile CountDownLatch uiChangeLatch = new CountDownLatch(1);
    private volatile String lastWindowPackage = "";
    private volatile String lastWindowClass = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Orb Eye v2.0 service connected");
        startHttpServer();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int type = event.getEventType();

        // Feature 1: Capture notifications
        if (type == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            captureNotification(event);
        }

        // Feature 3: Signal UI change for /wait
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Update current window info
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (event.getPackageName() != null) {
                    lastWindowPackage = event.getPackageName().toString();
                }
                if (event.getClassName() != null) {
                    lastWindowClass = event.getClassName().toString();
                }
            }
            // Release any waiting /wait calls
            uiChangeLatch.countDown();
        }
    }

    private void captureNotification(AccessibilityEvent event) {
        try {
            JSONObject notif = new JSONObject();
            notif.put("timestamp", System.currentTimeMillis());
            notif.put("package", event.getPackageName() != null ? event.getPackageName().toString() : "");

            // Extract text from event
            JSONArray textArr = new JSONArray();
            if (event.getText() != null) {
                for (CharSequence cs : event.getText()) {
                    if (cs != null) textArr.put(cs.toString());
                }
            }
            notif.put("text", textArr);

            // Try to get Notification extras
            Parcelable parcel = event.getParcelableData();
            if (parcel instanceof Notification) {
                Notification n = (Notification) parcel;
                if (n.extras != null) {
                    String title = n.extras.getString(Notification.EXTRA_TITLE, "");
                    CharSequence body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                    CharSequence bigText = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                    notif.put("title", title);
                    notif.put("body", body != null ? body.toString() : "");
                    if (bigText != null) notif.put("bigText", bigText.toString());
                }
            }

            notificationBuffer.add(notif);

            // Trim buffer
            while (notificationBuffer.size() > MAX_NOTIFICATIONS) {
                notificationBuffer.remove(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Notification capture error: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Orb Eye service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopHttpServer();
    }

    private void startHttpServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                Log.i(TAG, "HTTP server listening on port " + PORT);
                while (!Thread.interrupted()) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleRequest(client)).start();
                }
            } catch (Exception e) {
                Log.e(TAG, "Server error: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopHttpServer() {
        try {
            if (serverSocket != null) serverSocket.close();
            if (serverThread != null) serverThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Stop error: " + e.getMessage());
        }
    }

    private void handleRequest(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) { client.close(); return; }

            // Read headers
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // Read body if present
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                reader.read(buf, 0, contentLength);
                body = new String(buf);
            }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String path = parts[1];

            String response;
            try {
                response = routeRequest(method, path, body);
            } catch (Exception e) {
                response = errorJson("Internal error: " + e.getMessage());
            }

            OutputStream out = client.getOutputStream();
            String http = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Content-Length: " + response.getBytes("UTF-8").length + "\r\n"
                    + "\r\n"
                    + response;
            out.write(http.getBytes("UTF-8"));
            out.flush();
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "Request error: " + e.getMessage());
        }
    }

    private String routeRequest(String method, String path, String body) throws Exception {
        String route = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
        String query = path.contains("?") ? path.substring(path.indexOf("?") + 1) : "";

        switch (route) {
            case "/ping":
                return "{\"ok\":true,\"service\":\"orb-eye\",\"version\":\"2.0\"}";

            case "/tree":
                return getUiTree(query);

            case "/screen":
                return getScreenText();

            case "/focused":
                return getFocusedElement();

            case "/tap":
                return handleTap(new JSONObject(body));

            case "/click":
                return handleClick(new JSONObject(body));

            case "/input":
                return handleInput(new JSONObject(body));

            case "/setText":
                return handleSetText(new JSONObject(body));

            case "/scroll":
                return handleScroll(new JSONObject(body));

            case "/back":
                performGlobalAction(GLOBAL_ACTION_BACK);
                return "{\"ok\":true}";

            case "/home":
                performGlobalAction(GLOBAL_ACTION_HOME);
                return "{\"ok\":true}";

            case "/recents":
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                return "{\"ok\":true}";

            case "/notifications":
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                return "{\"ok\":true}";

            // === New endpoints ===

            case "/notify":
                return getNotifications(query);

            case "/info":
                return getAppInfo();

            case "/wait":
                return handleWait(query);

            case "/swipe":
                return handleSwipe(new JSONObject(body));

            case "/longpress":
                return handleLongPress(new JSONObject(body));

            default:
                return errorJson("Unknown route: " + route);
        }
    }

    // ===== Feature 1: Notifications =====

    private String getNotifications(String query) throws Exception {
        boolean clear = query.contains("clear=true");
        String sincePkg = null;
        if (query.contains("package=")) {
            sincePkg = query.split("package=")[1].split("&")[0];
        }

        JSONArray arr = new JSONArray();
        for (JSONObject n : notificationBuffer) {
            if (sincePkg != null && !n.optString("package").equals(sincePkg)) continue;
            arr.put(n);
        }

        if (clear) {
            notificationBuffer.clear();
        }

        JSONObject result = new JSONObject();
        result.put("notifications", arr);
        result.put("count", arr.length());
        return result.toString();
    }

    // ===== Feature 2: SET_TEXT (direct text injection) =====

    private String handleSetText(JSONObject body) throws Exception {
        String text = body.getString("text");
        String targetId = body.optString("id", "");
        boolean append = body.optBoolean("append", false);

        AccessibilityNodeInfo target = null;

        // Find target: by id, or focused, or first editable
        if (!targetId.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                var nodes = root.findAccessibilityNodeInfosByViewId(targetId);
                if (nodes != null && !nodes.isEmpty()) {
                    target = nodes.get(0);
                }
            }
        }

        if (target == null) {
            target = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }

        if (target == null) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                target = findFirstEditable(root);
            }
        }

        if (target == null) return errorJson("No editable field found");

        String newText = text;
        if (append && target.getText() != null) {
            newText = target.getText().toString() + text;
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
        boolean set = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

        JSONObject result = new JSONObject();
        result.put("ok", set);
        result.put("text", newText);
        return result.toString();
    }

    // ===== Feature 3: Wait for UI change =====

    private String handleWait(String query) throws Exception {
        long timeoutMs = 5000; // default 5s
        if (query.contains("timeout=")) {
            try {
                timeoutMs = Long.parseLong(query.split("timeout=")[1].split("&")[0]);
            } catch (Exception ignored) {}
        }

        // Reset latch
        uiChangeLatch = new CountDownLatch(1);

        // Wait for UI change or timeout
        boolean changed = uiChangeLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("changed", changed);
        result.put("timeoutMs", timeoutMs);
        result.put("package", lastWindowPackage);
        result.put("activity", lastWindowClass);
        return result.toString();
    }

    // ===== Feature 4: App Info =====

    private String getAppInfo() throws Exception {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("package", lastWindowPackage);
        result.put("activity", lastWindowClass);

        if (root != null) {
            result.put("windowPackage", root.getPackageName() != null ? root.getPackageName().toString() : "");
            int childCount = root.getChildCount();
            result.put("windowChildCount", childCount);
            root.recycle();
        }

        return result.toString();
    }

    // ===== Swipe gesture =====

    private String handleSwipe(JSONObject body) throws Exception {
        int x1 = body.getInt("x1");
        int y1 = body.getInt("y1");
        int x2 = body.getInt("x2");
        int y2 = body.getInt("y2");
        long duration = body.optLong("duration", 300);

        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        boolean dispatched = dispatchGesture(builder.build(), null, null);

        JSONObject result = new JSONObject();
        result.put("ok", dispatched);
        return result.toString();
    }

    // ===== Long press =====

    private String handleLongPress(JSONObject body) throws Exception {
        int x = body.getInt("x");
        int y = body.getInt("y");
        long duration = body.optLong("duration", 1000);

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        boolean dispatched = dispatchGesture(builder.build(), null, null);

        JSONObject result = new JSONObject();
        result.put("ok", dispatched);
        result.put("x", x);
        result.put("y", y);
        return result.toString();
    }

    // ===== UI Tree =====

    private String getUiTree(String query) throws Exception {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return errorJson("No active window");

        String filterPkg = null;
        if (query.contains("package=")) {
            filterPkg = query.split("package=")[1].split("&")[0];
        }

        JSONObject tree = nodeToJson(root, 0, 15, filterPkg);
        root.recycle();
        return tree.toString();
    }

    private JSONObject nodeToJson(AccessibilityNodeInfo node, int depth, int maxDepth, String filterPkg) throws Exception {
        JSONObject obj = new JSONObject();

        if (filterPkg != null && node.getPackageName() != null
                && !node.getPackageName().toString().equals(filterPkg)) {
            return null;
        }

        obj.put("class", node.getClassName() != null ? node.getClassName().toString() : "");
        obj.put("text", node.getText() != null ? node.getText().toString() : "");
        obj.put("desc", node.getContentDescription() != null ? node.getContentDescription().toString() : "");
        obj.put("id", node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "");
        obj.put("pkg", node.getPackageName() != null ? node.getPackageName().toString() : "");

        obj.put("clickable", node.isClickable());
        obj.put("editable", node.isEditable());
        obj.put("focused", node.isFocused());
        obj.put("selected", node.isSelected());
        obj.put("enabled", node.isEnabled());
        obj.put("scrollable", node.isScrollable());
        obj.put("checked", node.isChecked());

        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        obj.put("bounds", bounds.flattenToString());

        obj.put("hash", System.identityHashCode(node));

        if (depth < maxDepth) {
            JSONArray children = new JSONArray();
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    JSONObject childJson = nodeToJson(child, depth + 1, maxDepth, filterPkg);
                    if (childJson != null) {
                        children.put(childJson);
                    }
                    child.recycle();
                }
            }
            if (children.length() > 0) {
                obj.put("children", children);
            }
        }

        return obj;
    }

    // ===== Screen Text =====

    private String getScreenText() throws Exception {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return errorJson("No active window");

        JSONArray texts = new JSONArray();
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            String text = node.getText() != null ? node.getText().toString() : "";
            String desc = node.getContentDescription() != null ? node.getContentDescription().toString() : "";

            if (!text.isEmpty() || !desc.isEmpty()) {
                JSONObject item = new JSONObject();
                if (!text.isEmpty()) item.put("text", text);
                if (!desc.isEmpty()) item.put("desc", desc);
                item.put("clickable", node.isClickable());
                item.put("editable", node.isEditable());

                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);
                item.put("bounds", bounds.flattenToString());

                texts.put(item);
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }

        root.recycle();
        JSONObject result = new JSONObject();
        result.put("elements", texts);
        return result.toString();
    }

    // ===== Focused Element =====

    private String getFocusedElement() throws Exception {
        AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if (focused == null) return errorJson("No focused element");

        JSONObject obj = nodeToJson(focused, 0, 0, null);
        focused.recycle();
        return obj.toString();
    }

    // ===== Tap by coordinates =====

    private String handleTap(JSONObject body) throws Exception {
        int x = body.getInt("x");
        int y = body.getInt("y");
        long duration = body.optLong("duration", 100);

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        boolean dispatched = dispatchGesture(builder.build(), null, null);

        JSONObject result = new JSONObject();
        result.put("ok", dispatched);
        result.put("x", x);
        result.put("y", y);
        return result.toString();
    }

    // ===== Click by text/desc =====

    private String handleClick(JSONObject body) throws Exception {
        String targetText = body.optString("text", "");
        String targetDesc = body.optString("desc", "");
        String targetId = body.optString("id", "");
        String targetBounds = body.optString("bounds", "");

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return errorJson("No active window");

        AccessibilityNodeInfo target = null;

        if (!targetText.isEmpty()) {
            var nodes = root.findAccessibilityNodeInfosByText(targetText);
            if (nodes != null && !nodes.isEmpty()) {
                for (var n : nodes) {
                    if (n.isClickable()) { target = n; break; }
                }
                if (target == null) target = findClickableParent(nodes.get(0));
                if (target == null) target = nodes.get(0);
            }
        }

        if (target == null && !targetId.isEmpty()) {
            var nodes = root.findAccessibilityNodeInfosByViewId(targetId);
            if (nodes != null && !nodes.isEmpty()) {
                target = nodes.get(0);
            }
        }

        if (target == null && !targetDesc.isEmpty()) {
            target = findNodeByDesc(root, targetDesc);
        }

        // Click by bounds: find node matching bounds string, then tap center
        if (target == null && !targetBounds.isEmpty()) {
            target = findNodeByBounds(root, targetBounds);
        }

        if (target == null) {
            root.recycle();
            return errorJson("Element not found");
        }

        boolean clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (!clicked) {
            AccessibilityNodeInfo parent = findClickableParent(target);
            if (parent != null) {
                clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }

        JSONObject result = new JSONObject();
        result.put("ok", clicked);
        result.put("text", target.getText() != null ? target.getText().toString() : "");
        root.recycle();
        return result.toString();
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node.getParent();
        int depth = 0;
        while (current != null && depth < 5) {
            if (current.isClickable()) return current;
            current = current.getParent();
            depth++;
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByDesc(AccessibilityNodeInfo root, String desc) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node.getContentDescription() != null
                    && node.getContentDescription().toString().contains(desc)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByBounds(AccessibilityNodeInfo root, String boundsStr) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            if (bounds.flattenToString().equals(boundsStr)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    // ===== Input Text (legacy, uses SET_TEXT) =====

    private String handleInput(JSONObject body) throws Exception {
        String text = body.getString("text");
        boolean append = body.optBoolean("append", false);

        AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                focused = findFirstEditable(root);
            }
        }

        if (focused == null) return errorJson("No editable field found");

        if (!append) {
            Bundle clearArgs = new Bundle();
            clearArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs);
        }

        Bundle args = new Bundle();
        if (append && focused.getText() != null) {
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    focused.getText().toString() + text);
        } else {
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        }
        boolean set = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

        JSONObject result = new JSONObject();
        result.put("ok", set);
        return result.toString();
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo root) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node.isEditable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    // ===== Scroll =====

    private String handleScroll(JSONObject body) throws Exception {
        String direction = body.optString("direction", "down");

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return errorJson("No active window");

        AccessibilityNodeInfo scrollable = findFirstScrollable(root);
        if (scrollable == null) {
            root.recycle();
            return errorJson("No scrollable element");
        }

        int action = direction.equals("up") || direction.equals("left")
                ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;

        boolean scrolled = scrollable.performAction(action);
        root.recycle();

        JSONObject result = new JSONObject();
        result.put("ok", scrolled);
        result.put("direction", direction);
        return result.toString();
    }

    private AccessibilityNodeInfo findFirstScrollable(AccessibilityNodeInfo root) {
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node.isScrollable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private String errorJson(String msg) {
        return "{\"ok\":false,\"error\":\"" + msg.replace("\"", "'") + "\"}";
    }
}
