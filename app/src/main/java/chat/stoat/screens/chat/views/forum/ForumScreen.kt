package chat.stoat.screens.chat.views.forum

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.ULID
import chat.stoat.api.internals.has
import chat.stoat.api.routes.channel.CreateThreadBody
import chat.stoat.api.routes.channel.CreateThreadMessage
import chat.stoat.api.routes.channel.createThread
import chat.stoat.api.routes.channel.searchForumPosts
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.ForumLayout
import chat.stoat.core.model.schemas.ForumSortOrder
import chat.stoat.core.model.schemas.ForumTag
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.extensions.rememberChannelPermissions
import chat.stoat.internals.extensions.zero
import chat.stoat.screens.chat.LocalIsConnected
import chat.stoat.screens.chat.views.thread.relativeActivityTime
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 25

/**
 * Sort key of a post, newest first
 */
private fun sortKey(post: Channel, sort: ForumSortOrder): String {
    return if (sort == ForumSortOrder.LatestActivity) {
        post.lastMessageID ?: post.id!!
    } else {
        post.id!!
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ForumScreen(channelId: String, useDrawer: Boolean, onToggleDrawer: () -> Unit) {
    val channel = StoatAPI.channelCache[channelId] ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions by rememberChannelPermissions(channelId)

    var query by remember(channelId) { mutableStateOf("") }
    val selectedTags = remember(channelId) { mutableStateListOf<String>() }
    var sort by remember(channelId) {
        mutableStateOf(channel.defaultSortOrder ?: ForumSortOrder.LatestActivity)
    }
    var layout by remember(channelId) {
        mutableStateOf(channel.defaultLayout ?: ForumLayout.List)
    }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGuidelines by remember(channelId) { mutableStateOf(false) }
    var showNewPost by remember(channelId) { mutableStateOf(false) }

    val activeIds = remember(channelId) { mutableStateListOf<String>() }
    var hasMoreActive by remember(channelId) { mutableStateOf(false) }
    var showClosed by remember(channelId) { mutableStateOf(false) }
    val closedIds = remember(channelId) { mutableStateListOf<String>() }
    var hasMoreClosed by remember(channelId) { mutableStateOf(false) }
    var loading by remember(channelId) { mutableStateOf(false) }

    fun fetchPage(archived: Boolean, after: Channel? = null) {
        scope.launch {
            loading = true
            try {
                val list = searchForumPosts(
                    channelId,
                    tags = selectedTags.toList(),
                    sort = sort,
                    archived = archived,
                    before = after?.let { sortKey(it, sort) },
                    limit = PAGE_SIZE
                )
                val ids = list.threads.mapNotNull { it.id }
                val target = if (archived) closedIds else activeIds
                if (after == null) target.clear()
                target.addAll(ids)
                if (archived) hasMoreClosed = list.hasMore else hasMoreActive = list.hasMore
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.thread_error, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(channelId, sort, selectedTags.toList()) {
        fetchPage(false)
        if (showClosed) fetchPage(true)
    }

    fun arrange(posts: List<Channel>): List<Channel> {
        val search = query.trim().lowercase()
        return posts
            .filter { post ->
                (search.isEmpty() || post.name?.lowercase()?.contains(search) == true) &&
                        selectedTags.all { post.appliedTags?.contains(it) == true }
            }
            .sortedWith(
                compareByDescending<Channel> { it.pinned == true }
                    .thenByDescending { sortKey(it, sort) }
            )
    }

    // New posts arrive through events, so list everything open we know about
    val activePosts = arrange(
        StoatAPI.channelCache.values.filter {
            it.isThread && it.parent == channelId && it.archived != true
        }
    )
    val closedPosts = arrange(
        closedIds.mapNotNull { StoatAPI.channelCache[it] }.filter { it.archived == true }
    )

    val canPost = permissions has PermissionBit.CreatePublicThreads

    Scaffold(
        topBar = {
            Column {
                AnimatedVisibility(LocalIsConnected.current) {
                    Spacer(
                        Modifier.height(
                            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        )
                    )
                }
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_forum_24dp),
                                contentDescription = null
                            )
                            Text(
                                text = channel.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        if (useDrawer) {
                            IconButton(onClick = onToggleDrawer) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_menu_24dp),
                                    contentDescription = stringResource(R.string.menu)
                                )
                            }
                        }
                    },
                    actions = {
                        val gallery = layout == ForumLayout.Gallery
                        IconButton(onClick = {
                            layout = if (gallery) ForumLayout.List else ForumLayout.Gallery
                        }) {
                            Icon(
                                painter = painterResource(
                                    if (gallery) R.drawable.ic_list_24dp else R.drawable.ic_grid_view_24dp
                                ),
                                contentDescription = stringResource(
                                    if (gallery) R.string.forum_layout_list else R.string.forum_layout_gallery
                                )
                            )
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_sort_24dp),
                                contentDescription = stringResource(R.string.forum_sort)
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                ForumSortOrder.LatestActivity to R.string.forum_sort_latest,
                                ForumSortOrder.CreationDate to R.string.forum_sort_created
                            ).forEach { (order, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label)) },
                                    leadingIcon = {
                                        RadioButton(selected = sort == order, onClick = null)
                                    },
                                    onClick = {
                                        sort = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets.zero
                )
            }
        },
        floatingActionButton = {
            if (canPost) {
                ExtendedFloatingActionButton(
                    onClick = { showNewPost = true },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_comment_24dp),
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.forum_new_post)) },
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    ) { pv ->
        LazyColumn(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!channel.description.isNullOrBlank()) {
                item(key = "guidelines") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGuidelines = !showGuidelines }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.forum_guidelines),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = channel.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (showGuidelines) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.forum_search_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!channel.availableTags.isNullOrEmpty()) {
                item(key = "tags") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        channel.availableTags!!.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTags,
                                onClick = {
                                    if (tag.id in selectedTags) {
                                        selectedTags.remove(tag.id)
                                    } else {
                                        selectedTags.add(tag.id)
                                    }
                                },
                                label = { ForumTagLabel(tag) }
                            )
                        }
                    }
                }
            }

            if (activePosts.isEmpty() && !loading) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_forum_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                if (selectedTags.isEmpty() && query.isBlank()) R.string.forum_no_posts
                                else R.string.forum_no_matching_posts
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            forumPosts(activePosts, channel, layout, "active")

            if (hasMoreActive) {
                item(key = "more-active") {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            fetchPage(false, activeIds.lastOrNull()?.let { StoatAPI.channelCache[it] })
                        }
                    ) { Text(stringResource(R.string.forum_load_more)) }
                }
            }

            if (!showClosed) {
                item(key = "show-closed") {
                    TextButton(onClick = {
                        showClosed = true
                        fetchPage(true)
                    }) { Text(stringResource(R.string.forum_show_closed)) }
                }
            } else {
                item(key = "closed-heading") {
                    Text(
                        text = stringResource(R.string.forum_closed_posts),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                forumPosts(closedPosts, channel, layout, "closed")
                if (hasMoreClosed) {
                    item(key = "more-closed") {
                        TextButton(
                            enabled = !loading,
                            onClick = {
                                fetchPage(true, closedIds.lastOrNull()?.let { StoatAPI.channelCache[it] })
                            }
                        ) { Text(stringResource(R.string.forum_load_more)) }
                    }
                }
            }
        }
    }

    if (showNewPost) {
        NewForumPostSheet(
            forum = channel,
            canUseModeratedTags = permissions has PermissionBit.ManageThreads,
            onDismiss = { showNewPost = false }
        )
    }
}

/**
 * Posts as cards, or as a two column grid of tiles in the gallery layout
 */
private fun LazyListScope.forumPosts(
    posts: List<Channel>,
    forum: Channel,
    layout: ForumLayout,
    keyPrefix: String
) {
    if (layout == ForumLayout.Gallery) {
        items(posts.chunked(2), key = { "$keyPrefix-${it.first().id}" }) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(IntrinsicSize.Max)
            ) {
                row.forEach { post ->
                    ForumPostCard(
                        post = post,
                        forum = forum,
                        gallery = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    } else {
        items(posts, key = { "$keyPrefix-${it.id}" }) { post ->
            ForumPostCard(post = post, forum = forum)
        }
    }
}

/**
 * First image attached to the post's first message
 */
private fun postImage(post: Channel): AutumnResource? =
    StoatAPI.messageCache[post.id]?.attachments?.firstOrNull { it.metadata?.type == "Image" }

private fun AutumnResource.url() = "$STOAT_FILES/attachments/$id/$filename"

/**
 * Tag name with its emoji
 */
@Composable
fun ForumTagLabel(tag: ForumTag) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tag.emoji?.let { emoji ->
            if (emoji.length == 26 && emoji.all { it.isLetterOrDigit() }) {
                RemoteImage(
                    url = "$STOAT_FILES/emojis/$emoji",
                    description = null,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(emoji)
            }
        }
        Text(tag.name)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForumPostCard(
    post: Channel,
    forum: Channel,
    gallery: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val starter = StoatAPI.messageCache[post.id]
    val author = StoatAPI.userCache[post.owner ?: starter?.author]
    val tags = forum.availableTags?.filter { post.appliedTags?.contains(it.id) == true }
        ?: emptyList()
    val image = postImage(post)
    val lastActivity = remember(post.lastMessageID, post.id) {
        runCatching { ULID.asTimestamp(post.lastMessageID ?: post.id!!) }.getOrNull()
    }

    Card(
        onClick = { scope.launch { ActionChannel.send(Action.SwitchChannel(post.id!!)) } },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        if (gallery && image != null) {
            RemoteImage(
                url = image.url(),
                description = image.filename,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        }
        Row {
            Column(
                Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { tag ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                shape = CircleShape
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    ProvideSmallText { ForumTagLabel(tag) }
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (post.pinned == true) {
                        Icon(
                            painter = painterResource(R.drawable.ic_keep_24dp),
                            contentDescription = stringResource(R.string.forum_post_pinned),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (post.locked == true) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock_24dp),
                            contentDescription = stringResource(R.string.forum_post_locked),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = post.name ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                starter?.content?.takeIf { it.isNotBlank() }?.let { content ->
                    Text(
                        text = (author?.let { User.resolveDefaultName(it) + ": " } ?: "") + content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (gallery && image == null) 6 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val stats: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chat_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.forum_post_messages,
                                post.messageCount ?: 0,
                                post.messageCount ?: 0
                            ),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    lastActivity?.let {
                        Text(
                            text = relativeActivityTime(LocalContext.current, it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (post.archived == true) {
                        Text(
                            text = stringResource(R.string.forum_post_closed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // FlowRow under-reports its intrinsic height, which clips gallery rows
                if (gallery) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { stats() }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { stats() }
                }
            }
            if (!gallery && image != null) {
                RemoteImage(
                    url = image.url(),
                    description = image.filename,
                    modifier = Modifier
                        .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun ProvideSmallText(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelMedium,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewForumPostSheet(forum: Channel, canUseModeratedTags: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    var submitting by remember { mutableStateOf(false) }
    val idempotencyKey = remember { ULID.makeNext() }

    val selectableTags = forum.availableTags
        ?.filter { !it.moderated || canUseModeratedTags } ?: emptyList()
    val tagMissing = forum.requireTag == true && tags.isEmpty()
    val valid = title.isNotBlank() && message.isNotBlank() && !tagMissing

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.forum_new_post),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100) },
                label = { Text(stringResource(R.string.forum_post_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            if (selectableTags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.forum_post_tags),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectableTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in tags,
                            onClick = {
                                if (tag.id in tags) tags.remove(tag.id)
                                else if (tags.size < 5) tags.add(tag.id)
                            },
                            label = { ForumTagLabel(tag) }
                        )
                    }
                }
                if (tagMissing) {
                    Text(
                        text = stringResource(R.string.forum_post_tag_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.forum_post_message)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = valid && !submitting,
                onClick = {
                    submitting = true
                    scope.launch {
                        try {
                            val post = createThread(
                                forum.id!!,
                                CreateThreadBody(
                                    name = title.trim(),
                                    message = CreateThreadMessage(content = message.trim()),
                                    appliedTags = tags.toList()
                                ),
                                idempotencyKey
                            )
                            // Creating a post joins it
                            onDismiss()
                            ActionChannel.send(Action.SwitchChannel(post.id!!))
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.thread_error, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            submitting = false
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.forum_post_submit))
            }
        }
    }
}
