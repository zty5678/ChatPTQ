package page.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.FastSendMode
import config.LocalAppConfig
import example.imageviewer.view.Tooltip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import repository.api.ChatCompletionService
import repository.data_store.DSKey
import repository.data_store.getFromDataStore
import repository.data_store.saveToDataStore
import repository.service.request
import util.LogUtils
import util.copyToClipboard
import view.Loading
import view.LocalAppToaster
import java.awt.event.KeyEvent

@Composable
fun ChatPage() {
    val conversations = remember { mutableStateListOf<Conversation>() }

    var inputEnabled by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }
    var textFieldValueState by remember { mutableStateOf(TextFieldValue(text = "")) }

    val scrollState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()
    val toaster = LocalAppToaster.current
    val config = LocalAppConfig.current.appConfig
    var sendFastKeyDownTime by remember { mutableStateOf(0L) }

    remember(conversations.size) {
        coroutineScope.launch {
            delay(200)
            scrollState.animateScrollToItem(conversations.size)
        }
    }

    DisposableEffect(Unit) {
        val session = DSKey.chatConversations.getFromDataStore(default = Session())
        conversations.clear()
        conversations += session.conversations

        onDispose {
            Session(conversations).saveToDataStore(DSKey.chatConversations)
        }
    }

    val sendChat = sendChat@{
        var input = textFieldValueState.text
        if (input.isBlank()) {
            toaster.toastFailure("请输入内容")
            return@sendChat
        }

        if (isSending) return@sendChat

        textFieldValueState = textFieldValueState.copy(text = "")

        coroutineScope.launch {
            isSending = true
            //去掉因Enter快捷键产生的空行
            input = input.replace("((\r\n)|\n)[\\s\t ]*(\\1)+".toRegex(), "\n").trim()

            val message = Message(content = input)
            conversations += Conversation(message)

            request<ChatCompletionService> {
                val res = chat(ChatRequest(messages = conversations.filter { it.success }.map { it.message }))
                val last = conversations.removeLast()
                conversations += last.copy(tokenUsage = res.usage.prompt_tokens)
                conversations += Conversation(res.choices[0].message, res.usage.completion_tokens)
            }.failure {
                val last = conversations.removeLast()
                conversations += last.copy(success = false)
            }.success {
                input = ""
            }.whatEver {
                isSending = false
            }.toastException(toaster)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = scrollState, modifier = Modifier.fillMaxWidth().weight(6f)) {
            val size = conversations.size
            items(size) { conversationIndex ->
                val conversation = conversations[conversationIndex]
                val message = conversation.message
                val success = conversation.success
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        message.content,
                        onValueChange = {},
                        label = {
                            Text(
                                (if (message.role == "assistant") config.gptName else "我") + if (conversation.tokenUsage == null) "" else " (${conversation.tokenUsage}tks)",
                                modifier = Modifier
                                    .align(if (message.role == "assistant") Alignment.Start else Alignment.End)
                            )
                        },
                        modifier = Modifier.align(if (message.role == "assistant") Alignment.Start else Alignment.End),
                        isError = !success
                    )
                    Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        Text("${conversationIndex + 1}/$size", fontSize = 14.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.width(5.dp))

                        IconButton(iconPath = "icon_translate_to_eng.png", tooltipText = "将内容翻译成英文") {
                            textFieldValueState =
                                textFieldValueState.copy(text = "Translate the following into English please:\n${message.content}")
                            sendChat()
                        }

                        IconButton(iconPath = "icon_translate.png", tooltipText = "将内容翻译成中文") {
                            textFieldValueState =
                                textFieldValueState.copy("Translate the following into Chinese please:\n${message.content}")
                            sendChat()
                        }

                        IconButton(iconPath = "icon_copy.png", tooltipText = "复制内容到剪贴板") {
                            copyToClipboard(message.content)
                        }
                    }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(vertical = 5.dp).fillMaxWidth(),
            color = Color(233, 233, 233),
            thickness = 1.5.dp
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().weight(1f)) {
            OutlinedTextField(
                value = textFieldValueState,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent {
                        when (FastSendMode.valueOf(config.fastSendMode)) {
                            FastSendMode.LongPressEnter -> {
                                if (it.key == Key(KeyEvent.VK_ENTER)) {
                                    return@onPreviewKeyEvent if (it.type == KeyEventType.KeyDown) {
                                        if (sendFastKeyDownTime == 0L) {
                                            sendFastKeyDownTime = System.currentTimeMillis()
                                            false
                                        } else {
                                            if (System.currentTimeMillis() - sendFastKeyDownTime > config.fastSendLongPressDuration) {
                                                sendFastKeyDownTime = 0L
                                                sendChat()
                                                true
                                            } else {
                                                inputEnabled = false
                                                false
                                            }
                                        }
                                    } else if (it.type == KeyEventType.KeyUp) {
                                        sendFastKeyDownTime = 0L
                                        inputEnabled = true
                                        false
                                    } else false
                                }
                                false
                            }

                            FastSendMode.ShiftEnter -> {
                                if (it.key == Key(KeyEvent.VK_ENTER) && it.type == KeyEventType.KeyUp) {
                                    if (!it.isShiftPressed) {
                                        sendChat()
                                        true
                                    } else {
                                        val selectionStartText = textFieldValueState.text.subSequence(0, textFieldValueState.selection.start)
                                        val selectionEndText = textFieldValueState.text.subSequence(textFieldValueState.selection.end, textFieldValueState.text.length)

                                        val newText = "" + selectionStartText + "\n" + selectionEndText
                                        val newSelectionStartInt = textFieldValueState.selection.start + 1;

                                        textFieldValueState =
                                            textFieldValueState.copy(
                                                text = newText,
                                                selection = TextRange(newSelectionStartInt)
                                            )
                                        LogUtils.d("input=${textFieldValueState.text}======")
                                        false
                                    }
                                } else false
                            }

                            FastSendMode.ControlEnter -> {
                                if (it.key == Key(KeyEvent.VK_ENTER) && it.type == KeyEventType.KeyUp) {
                                    if (!it.isCtrlPressed) {
                                        sendChat()
                                        true
                                    } else {
                                        val newText = textFieldValueState.text + "\n"
                                        textFieldValueState =
                                            textFieldValueState.copy(
                                                text = newText,
                                                selection = TextRange(newText.length)
                                            )
                                        false
                                    }
                                } else false
                            }

                            else -> {
                                false
                            }
                        }
                    },
                placeholder = {
                    Text("和${config.gptName}聊天...", color = Color.LightGray)
                },
                onValueChange = {
                    if (!inputEnabled || isSending) return@OutlinedTextField
                    textFieldValueState = it
                },
                leadingIcon = if (isSending) {
                    { Loading() }
                } else null
            )
            Box(modifier = Modifier.size(80.dp).padding(start=10.dp)) {
                IconButton(modifier = Modifier.align(Alignment.TopStart),
                    iconPath = "icon_send.png",
                    tooltipText = "发送") {
                    sendChat()
                }
                IconButton(modifier = Modifier.align(Alignment.TopEnd), iconPath = "icon_delete.png",
                    tooltipText = "清空聊天记录") {
                    conversations.clear()
                }
                IconButton(
                    modifier = Modifier.align(Alignment.BottomStart),
                    iconPath = "icon_translate.png",
                    tooltipText = "将输入内容翻译成中文"
                ) {
                    if (textFieldValueState.text.isBlank()) {
                        toaster.toastFailure("请输入翻译内容")
                        return@IconButton
                    }
                    textFieldValueState = textFieldValueState.copy("翻译成中文:\n${textFieldValueState.text}")
                    sendChat()
                }
                IconButton(modifier = Modifier.align(Alignment.BottomEnd), iconPath = "icon_translate_to_eng.png",
                    tooltipText = "将输入内容翻译成英文") {
                    if (textFieldValueState.text.isBlank()) {
                        toaster.toastFailure("请输入翻译内容")
                        return@IconButton
                    }
                    textFieldValueState =
                        textFieldValueState.copy("Translate the following into English please:\n${textFieldValueState.text}")
                    sendChat()
                }
            }
        }
    }
}

@Composable
fun IconButton(tooltipText: String = "", modifier: Modifier = Modifier, iconPath: String, onClick: () -> Unit) {

   if (tooltipText.isBlank()) {
        TextButton(
            onClick = onClick,
            modifier = modifier.size(40.dp)
        ) {
            Icon(painterResource(iconPath), null, modifier = Modifier.size(18.dp))
        }
    } else {
        Tooltip(text = tooltipText, modifier=modifier) {
            TextButton(
                onClick = onClick,
                modifier = modifier.size(40.dp)
            ) {
                Icon(painterResource(iconPath), null, modifier = Modifier.size(18.dp))
            }
        }
    }
}