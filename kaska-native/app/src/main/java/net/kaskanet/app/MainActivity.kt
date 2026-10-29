package net.kaskanet.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private const val API = "https://kaskaneeet.duckdns.org"
private val Ink = Color(0xFF090B14)
private val Panel = Color(0xFF151827)
private val Accent = Color(0xFF8B5CF6)
private val Muted = Color(0xFF9AA1B5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kaskanet", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
                KaskaApp(
                    initialToken = prefs.getString("token", null),
                    saveToken = { prefs.edit().putString("token", it).apply() },
                    clearToken = { prefs.edit().remove("token").apply() },
                    openUrl = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                )
            }
        }
    }
}

private object Api {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    suspend fun call(path: String, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(API + path).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toString().toRequestBody(jsonType)) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val data = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (!response.isSuccessful) throw IOException(data.optString("error", "Ошибка сервера (${response.code})"))
            data
        }
    }
}

@Composable private fun KaskaApp(initialToken: String?, saveToken: (String) -> Unit, clearToken: () -> Unit, openUrl: (String) -> Unit) {
    var token by remember { mutableStateOf(initialToken) }
    var screen by remember { mutableStateOf(if (initialToken == null) "auth" else "chats") }
    var authMode by remember { mutableStateOf("login") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedChat by remember { mutableStateOf<JSONObject?>(null) }
    var chats by remember { mutableStateOf(listOf<JSONObject>()) }
    var messages by remember { mutableStateOf(listOf<JSONObject>()) }
    var feed by remember { mutableStateOf(listOf<JSONObject>()) }
    var profile by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var postDraft by remember { mutableStateOf("") }
    var musicQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(listOf<JSONObject>()) }
    val scope = rememberCoroutineScope()

    suspend fun loadChats() { val a = Api.call("/api/convs", token); val arr = a.optJSONArray("convs") ?: JSONArray(); chats = (0 until arr.length()).map { arr.getJSONObject(it) } }
    suspend fun loadMessages() { val id = selectedChat?.optInt("id") ?: return; val a = Api.call("/api/msgs?id=$id", token); val arr = a.optJSONArray("messages") ?: JSONArray(); messages = (0 until arr.length()).map { arr.getJSONObject(it) } }
    suspend fun loadFeed() { val a = Api.call("/api/feed", token); val arr = a.optJSONArray("posts") ?: JSONArray(); feed = (0 until arr.length()).map { arr.getJSONObject(it) } }
    suspend fun loadProfile() { profile = Api.call("/api/me", token).optJSONObject("user") }
    suspend fun loadMusic(q: String) { val a = Api.call("/api/music/search?q=" + URLEncoder.encode(q, "UTF-8"), token); val arr = a.optJSONArray("tracks") ?: JSONArray(); tracks = (0 until arr.length()).map { arr.getJSONObject(it) } }

    LaunchedEffect(token, screen, selectedChat?.optInt("id")) {
        if (token != null) {
            try {
                when (screen) { "chats" -> loadChats(); "chat" -> { loadProfile(); loadMessages() }; "feed" -> loadFeed(); "profile" -> loadProfile() }
            } catch (e: Exception) { error = e.message ?: "Нет соединения с сервером" }
            if (screen == "chats" || screen == "chat") while (true) {
                delay(5000)
                try { if (screen == "chats") loadChats() else if (screen == "chat") loadMessages() } catch (_: Exception) { }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = Ink) {
        if (token == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("KaskaNet", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Твоя связь. Твой мир.", color = Muted, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = authMode == "login", onClick = { authMode = "login"; error = "" }, label = { Text("Войти") })
                    FilterChip(selected = authMode == "register", onClick = { authMode = "register"; error = "" }, label = { Text("Создать аккаунт") })
                }
                OutlinedTextField(email, { email = it }, label = { Text("Электронная почта") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (authMode == "register") Text("Пароль должен содержать не менее 8 символов", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF7B86), modifier = Modifier.padding(top = 12.dp))
                Button(onClick = {
                    if (email.isBlank() || password.isBlank()) { error = "Заполни почту и пароль"; return@Button }
                    busy = true; error = ""
                    scope.launch { try { val data = Api.call(if (authMode == "login") "/api/login" else "/api/register", body = JSONObject().put("email", email.trim()).put("password", password)); token = data.getString("token"); saveToken(token!!); screen = "chats" } catch (e: Exception) { error = e.message ?: "Ошибка" } finally { busy = false } }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), shape = RoundedCornerShape(14.dp)) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (authMode == "login") "Войти в KaskaNet" else "Зарегистрироваться") }
            }
        } else if (screen == "chat" && selectedChat != null) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Panel).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { screen = "chats" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                    Column(Modifier.weight(1f)) { Text(selectedChat!!.optString("name", "Чат"), fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("KaskaNet · синхронизация", color = Muted, fontSize = 12.sp) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(messages, key = { it.optInt("id") }) { msg ->
                        val mine = msg.optInt("from") == (profile?.optInt("id") ?: -1)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max = 300.dp).background(if (mine) Color(0xFF49318A) else Panel, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 9.dp)) {
                                if (!mine) Text(msg.optString("name", "Пользователь"), color = Color(0xFFC4B5FD), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(msg.optString("text", when (msg.optString("kind")) { "voice" -> "🎤 Голосовое сообщение"; "photo" -> "🖼 Фото"; "video" -> "🎬 Видео"; else -> "Вложение" }), color = Color.White)
                                if (msg.optBoolean("edited")) Text("изменено", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение…") }, maxLines = 4)
                    IconButton(enabled = draft.isNotBlank() && !busy, onClick = { val text = draft.trim(); draft = ""; scope.launch { try { Api.call("/api/send", token, JSONObject().put("conv", selectedChat!!.optInt("id")).put("kind", "text").put("text", text)); loadMessages(); loadChats() } catch (e: Exception) { error = e.message ?: "Не удалось отправить" } } }) { Icon(Icons.Default.Send, contentDescription = "Отправить", tint = Accent) }
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF7B86), modifier = Modifier.padding(8.dp))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("KaskaNet", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold); Text(when (screen) { "feed" -> "Лента"; "profile" -> "Профиль"; "music" -> "Музыка"; else -> "Сообщения" }, color = Muted) }
                    Box(Modifier.size(42.dp).background(Accent.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFFC4B5FD)) }
                }
                when (screen) {
                    "feed" -> Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(postDraft, { postDraft = it }, modifier = Modifier.weight(1f), placeholder = { Text("Что нового?") }, maxLines = 3)
                            IconButton(enabled = postDraft.isNotBlank() && !busy, onClick = { val content = postDraft.trim(); busy = true; scope.launch { try { Api.call("/api/feed", token, JSONObject().put("text", content)); postDraft = ""; loadFeed() } catch (e: Exception) { error = e.message ?: "Не удалось опубликовать" } finally { busy = false } } }) { Icon(Icons.Default.Send, contentDescription = "Опубликовать", tint = Accent) }
                        }
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7B86), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(feed, key = { it.optInt("id") }) { post ->
                                Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                                    Text(post.optString("name", "Пользователь"), fontWeight = FontWeight.Bold)
                                    Text("@" + post.optString("username", "user"), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                                    if (post.optString("text").isNotBlank()) Text(post.optString("text"), fontSize = 16.sp)
                                    if (post.optString("link").isNotBlank()) Text(post.optString("link"), color = Color(0xFFC4B5FD), modifier = Modifier.padding(top = 6.dp))
                                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { scope.launch { try { Api.call("/api/feed/like", token, JSONObject().put("id", post.optInt("id"))); loadFeed() } catch (e: Exception) { error = e.message ?: "Ошибка реакции" } }) { Icon(if (post.optBoolean("liked")) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (post.optBoolean("liked")) Color(0xFFFF6B9A) else Muted) }
                                        Text("${post.optInt("likes")}", color = Muted)
                                        Spacer(Modifier.width(14.dp)); Icon(Icons.Default.ChatBubbleOutline, null, tint = Muted, modifier = Modifier.size(18.dp)); Text(" ${post.optInt("comments")}", color = Muted)
                                    }
                                }
                            }
                        }
                    }
                    "music" -> Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(musicQuery, { musicQuery = it }, modifier = Modifier.weight(1f), placeholder = { Text("Найти музыку") }, singleLine = true)
                            IconButton(enabled = musicQuery.isNotBlank() && !busy, onClick = { busy = true; error = ""; scope.launch { try { loadMusic(musicQuery.trim()) } catch (e: Exception) { error = e.message ?: "Поиск музыки недоступен" } finally { busy = false } } }) { Icon(Icons.Default.Search, null, tint = Accent) }
                        }
                        Text("Поиск через сервер KaskaNet / SoundCloud", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7B86))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tracks, key = { it.optString("id") }) { track ->
                                Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable { openUrl(track.optString("url")) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, null, tint = Accent, modifier = Modifier.size(28.dp))
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(track.optString("title", "Без названия"), fontWeight = FontWeight.SemiBold, maxLines = 2); Text(track.optString("artist", "Исполнитель"), color = Muted, fontSize = 12.sp) }
                                    Icon(Icons.Default.OpenInNew, null, tint = Muted)
                                }
                            }
                        }
                    }
                    "profile" -> Column(Modifier.weight(1f).padding(18.dp)) {
                        Text(profile?.optString("name", "KaskaNet пользователь") ?: "Загрузка профиля…", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("@" + (profile?.optString("username", "") ?: ""), color = Muted)
                        Spacer(Modifier.height(10.dp)); Text(profile?.optString("bio", "") ?: "", color = Color.White)
                        Text(profile?.optString("email", "") ?: "", color = Muted, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(22.dp)); OutlinedButton(onClick = { scope.launch { try { Api.call("/api/logout", token, JSONObject()); clearToken(); token = null; profile = null; screen = "auth" } catch (e: Exception) { error = e.message ?: "Ошибка выхода" } } }) { Icon(Icons.Default.Logout, null); Spacer(Modifier.width(8.dp)); Text("Выйти") }
                        if (error.isNotBlank()) Text(error, color = Color(0xFFFF7B86))
                    }
                    else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (chats.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Forum, null, tint = Muted, modifier = Modifier.size(48.dp)); Text("Пока нет чатов", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); Text("Начни переписку в KaskaNet", color = Muted, fontSize = 13.sp) } }
                        items(chats, key = { it.optInt("id") }) { chat ->
                            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { selectedChat = chat; screen = "chat"; error = "" }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).background(Accent.copy(alpha = .24f), CircleShape), contentAlignment = Alignment.Center) { Text(chat.optString("name", "K").take(1).uppercase(), color = Color(0xFFD8CCFF), fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(chat.optString("name", "Чат"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp); Text(chat.optString("last_text", "Нет сообщений"), color = Muted, maxLines = 1, fontSize = 13.sp) }
                                if (chat.optBoolean("fav")) Icon(Icons.Default.PushPin, null, tint = Accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(selected = screen == "chats" || screen == "chat", onClick = { screen = "chats"; selectedChat = null; error = "" }, icon = { Icon(Icons.Default.ChatBubbleOutline, null) }, label = { Text("Чаты") })
                    NavigationBarItem(selected = screen == "feed", onClick = { screen = "feed"; error = "" }, icon = { Icon(Icons.Default.DynamicFeed, null) }, label = { Text("Лента") })
                    NavigationBarItem(selected = screen == "music", onClick = { screen = "music"; error = "" }, icon = { Icon(Icons.Default.MusicNote, null) }, label = { Text("Музыка") })
                    NavigationBarItem(selected = screen == "profile", onClick = { screen = "profile"; error = "" }, icon = { Icon(Icons.Default.PersonOutline, null) }, label = { Text("Профиль") })
                }
            }
        }
    }
}
