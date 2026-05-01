package com.example.foodbridge.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.foodbridge.navigation.Screen
import com.example.foodbridge.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class NotificationItem(
    val id: String = "",
    val icon: ImageVector = Icons.Default.Notifications,
    val title: String = "",
    val message: String = "",
    val time: String = "",
    val createdAt: Long = 0L,
    val isUnread: Boolean = true,
    val type: String = ""
)

private enum class NotificationTypeFilter(val label: String) {
    ALL("All types"),
    DONATIONS("Donations"),
    CLAIMS("Claims"),
    GENERAL("Updates")
}

private val NotificationBrand = GreenPrimary
private val NotificationInk = Color(0xFF121212)
private val NotificationMuted = Color(0xFF6F7481)
private val NotificationSurface = Color(0xFFFFFFFF)
private val NotificationSurfaceAlt = Color(0xFFF6F7F9)
private val NotificationBorder = Color(0xFFE7E9EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedTypeFilter by remember { mutableStateOf(NotificationTypeFilter.ALL) }
    var notifications by remember { mutableStateOf<List<NotificationItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var temporaryUnreadHintCount by remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    DisposableEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        var listenerReg: ListenerRegistration? = null

        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val currentUserRole = doc.getString("role") ?: "DONOR"
                    val recipientIds = if (currentUserRole == "RECEIVER") {
                        listOf(uid, "ALL")
                    } else {
                        listOf(uid)
                    }

                    listenerReg = db.collection("notifications")
                        .whereIn("recipientId", recipientIds)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null || snapshot == null) {
                                isLoading = false
                                return@addSnapshotListener
                            }

                            notifications = snapshot.documents.mapNotNull { docSnap ->
                                val type = docSnap.getString("type") ?: return@mapNotNull null
                                val createdAt = docSnap.getLong("createdAt") ?: 0L
                                NotificationItem(
                                    id = docSnap.id,
                                    icon = notificationIcon(type),
                                    title = docSnap.getString("title") ?: "",
                                    message = docSnap.getString("message") ?: "",
                                    time = formatNotificationTime(createdAt),
                                    createdAt = createdAt,
                                    isUnread = docSnap.getBoolean("isUnread") ?: true,
                                    type = type
                                )
                            }
                            isLoading = false
                        }
                }
                .addOnFailureListener {
                    isLoading = false
                }
        } else {
            isLoading = false
        }

        onDispose {
            listenerReg?.remove()
        }
    }

    val unreadNotifications = notifications.filter { it.isUnread }
    val readNotifications = notifications.filter { !it.isUnread }
    val displayedNotifications = notifications
        .filter { it.matchesTypeFilter(selectedTypeFilter) }
        .filter {
            when (selectedTab) {
                1 -> it.isUnread
                2 -> !it.isUnread
                else -> true
            }
        }

    LaunchedEffect(isLoading, unreadNotifications.size) {
        if (isLoading || unreadNotifications.isEmpty()) {
            temporaryUnreadHintCount = null
            return@LaunchedEffect
        }

        temporaryUnreadHintCount = unreadNotifications.size
        delay(2400)
        temporaryUnreadHintCount = null
    }

    fun updateNotificationReadState(notificationId: String, unread: Boolean, successMessage: String) {
        scope.launch {
            runCatching {
                db.collection("notifications")
                    .document(notificationId)
                    .update("isUnread", unread)
                    .await()
            }.onSuccess {
                snackbarHostState.showSnackbar(successMessage)
            }.onFailure {
                snackbarHostState.showSnackbar("Couldn't update notification")
            }
        }
    }

    fun markAllAsRead() {
        if (unreadNotifications.isEmpty()) return
        scope.launch {
            runCatching {
                db.runBatch { batch ->
                    unreadNotifications.forEach { notif ->
                        batch.update(
                            db.collection("notifications").document(notif.id),
                            "isUnread",
                            false
                        )
                    }
                }.await()
            }.onSuccess {
                snackbarHostState.showSnackbar("All notifications marked as read")
            }.onFailure {
                snackbarHostState.showSnackbar("Couldn't mark all as read")
            }
        }
    }

    fun clearReadNotifications() {
        if (readNotifications.isEmpty()) return
        scope.launch {
            runCatching {
                db.runBatch { batch ->
                    readNotifications.forEach { notif ->
                        batch.delete(db.collection("notifications").document(notif.id))
                    }
                }.await()
            }.onSuccess {
                snackbarHostState.showSnackbar("Read notifications cleared")
            }.onFailure {
                snackbarHostState.showSnackbar("Couldn't clear read notifications")
            }
        }
    }

    fun openNotification(notif: NotificationItem) {
        scope.launch {
            if (notif.isUnread) {
                runCatching {
                    db.collection("notifications")
                        .document(notif.id)
                        .update("isUnread", false)
                        .await()
                }
            }

            when (notificationTargetRoute(notif.type)) {
                Screen.Browse.route -> navController.navigate(Screen.Browse.route)
                Screen.History.route -> navController.navigate(Screen.History.route)
                else -> Unit
            }
        }
    }

    Scaffold(
        containerColor = BackgroundLight,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = TextPrimary,
                    contentColor = SurfaceWhite,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("Notifications", fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    if (unreadNotifications.isNotEmpty()) {
                        IconButton(onClick = ::markAllAsRead) {
                            Icon(Icons.Default.DoneAll, null, tint = GreenPrimary)
                        }
                    }
                    if (readNotifications.isNotEmpty()) {
                        IconButton(onClick = ::clearReadNotifications) {
                            Icon(Icons.Default.DeleteSweep, null, tint = GreenPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    scrolledContainerColor = SurfaceWhite
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
        ) {
            temporaryUnreadHintCount?.let { unreadCount ->
                TemporaryUnreadHint(
                    unreadCount = unreadCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            NotificationSegmentedTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NotificationTypeFilter.values().toList()) { filter ->
                    FilterChip(
                        selected = selectedTypeFilter == filter,
                        onClick = { selectedTypeFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = NotificationSurface,
                            labelColor = NotificationInk,
                            selectedContainerColor = Color(0xFFFFEEE9),
                            selectedLabelColor = NotificationBrand
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = NotificationBorder,
                            selectedBorderColor = NotificationBrand.copy(alpha = 0.22f),
                            enabled = true,
                            selected = selectedTypeFilter == filter
                        ),
                        leadingIcon = {
                            if (selectedTypeFilter == filter) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = NotificationBrand
                                )
                            }
                        }
                    )
                }
            }

            when {
                isLoading -> {
                    NotificationLoadingSkeleton(modifier = Modifier.weight(1f))
                }

                displayedNotifications.isEmpty() -> {
                    EmptyNotificationsState(
                        modifier = Modifier.weight(1f),
                        selectedTab = selectedTab,
                        selectedTypeFilter = selectedTypeFilter,
                        onResetFilters = {
                            selectedTab = 0
                            selectedTypeFilter = NotificationTypeFilter.ALL
                        }
                    )
                }

                else -> {
                    val groupedNotifications = displayedNotifications.groupBy {
                        notificationSectionTitle(it.createdAt)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedNotifications.forEach { (section, sectionItems) ->
                            item(key = "header_$section") {
                                NotificationSectionHeader(section = section)
                            }

                            items(sectionItems, key = { it.id }) { notif ->
                                NotificationCard(
                                    notif = notif,
                                    onTap = { openNotification(notif) },
                                    onSwipeMarkRead = {
                                        updateNotificationReadState(
                                            notificationId = notif.id,
                                            unread = false,
                                            successMessage = "Marked as read"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GreenContainer)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NotificationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .height(14.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NotificationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.90f)
                                .height(10.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        NotificationSkeletonLine(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSkeletonLine(
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextSecondary.copy(alpha = 0.14f))
    )
}

@Composable
private fun TemporaryUnreadHint(
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = GreenContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = GreenPrimary.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$unreadCount unread update${if (unreadCount == 1) "" else "s"}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Swipe left or use the top actions.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSegmentedTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("All", "Unread", "Read")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(NotificationSurfaceAlt)
            .border(1.dp, NotificationBorder, RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) NotificationInk else Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) NotificationSurface else NotificationMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationSectionHeader(section: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = section,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(DividerColor.copy(alpha = 0.75f))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(
    notif: NotificationItem,
    onTap: () -> Unit,
    onSwipeMarkRead: () -> Unit
) {
    val accentColor = notificationAccentColor(notif.type)
    val iconTint = notificationAccentColor(notif.type)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && notif.isUnread) {
                onSwipeMarkRead()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = notif.isUnread,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1B1D23), Color(0xFF2B2E36))
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = NotificationSurface)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mark read",
                        color = NotificationSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) {
        Card(
            onClick = onTap,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (notif.isUnread) 10.dp else 4.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color(0x14000000),
                    spotColor = Color(0x14000000)
                ),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = NotificationSurface),
            elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (notif.isUnread) accentColor.copy(alpha = 0.18f) else NotificationBorder
            )
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(92.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (notif.isUnread) accentColor else Color.Transparent)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(iconTint.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(notif.icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            notif.title,
                            fontWeight = if (notif.isUnread) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 17.sp,
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (notif.isUnread) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFFFFEEE9))
                                    .border(
                                        width = 1.dp,
                                        color = accentColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                            {
                                Text(
                                    "New",
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(NotificationSurfaceAlt)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = notificationTypeLabel(notif.type),
                                fontSize = 10.sp,
                                color = iconTint,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(notif.time, fontSize = 11.sp, color = NotificationMuted)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        notif.message,
                        fontSize = 14.sp,
                        color = NotificationInk.copy(alpha = 0.86f),
                        lineHeight = 20.sp
                    )

                    notificationTargetLabel(notif.type)?.let { targetLabel ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = NotificationSurfaceAlt
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ArrowOutward,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open $targetLabel",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (notif.isUnread) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Swipe left to mark as read",
                            color = NotificationMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotificationsState(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    selectedTypeFilter: NotificationTypeFilter,
    onResetFilters: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.NotificationsNone,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (selectedTab == 0 && selectedTypeFilter == NotificationTypeFilter.ALL) {
                    "No notifications yet"
                } else {
                    "No notifications match these filters"
                },
                color = TextSecondary
            )
            if (selectedTab != 0 || selectedTypeFilter != NotificationTypeFilter.ALL) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onResetFilters) {
                    Text("Reset filters", color = GreenPrimary)
                }
            }
        }
    }
}

private fun notificationIcon(type: String): ImageVector = when (type) {
    "NEW_DONATION" -> Icons.Default.Fastfood
    "CLAIMED" -> Icons.Default.CheckCircle
    else -> Icons.Default.Notifications
}

private fun notificationAccentColor(type: String): Color = when (type) {
    "NEW_DONATION" -> NotificationBrand
    "CLAIMED" -> Color(0xFFFF9F1C)
    "COMPLETED" -> Color(0xFF111827)
    "IN_PROGRESS" -> Color(0xFF3B82F6)
    else -> Color(0xFF4F6D7A)
}

private fun notificationTypeLabel(type: String): String = when (type) {
    "NEW_DONATION" -> "Donation"
    "CLAIMED" -> "Claim update"
    "COMPLETED" -> "Completed"
    "IN_PROGRESS" -> "In progress"
    else -> "General"
}

private fun notificationTargetRoute(type: String): String? = when (type) {
    "NEW_DONATION" -> Screen.Browse.route
    "CLAIMED" -> Screen.History.route
    "COMPLETED" -> Screen.History.route
    "IN_PROGRESS" -> Screen.History.route
    else -> null
}

private fun notificationTargetLabel(type: String): String? = when (type) {
    "NEW_DONATION" -> "Browse donations"
    "CLAIMED", "COMPLETED", "IN_PROGRESS" -> "History"
    else -> null
}

private fun NotificationItem.matchesTypeFilter(filter: NotificationTypeFilter): Boolean = when (filter) {
    NotificationTypeFilter.ALL -> true
    NotificationTypeFilter.DONATIONS -> type == "NEW_DONATION"
    NotificationTypeFilter.CLAIMS -> type == "CLAIMED" || type == "COMPLETED" || type == "IN_PROGRESS"
    NotificationTypeFilter.GENERAL -> type != "NEW_DONATION" &&
        type != "CLAIMED" &&
        type != "COMPLETED" &&
        type != "IN_PROGRESS"
}

private fun formatNotificationTime(createdAt: Long): String {
    if (createdAt <= 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - createdAt
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        else -> SimpleDateFormat("dd MMM • hh:mm a", Locale.getDefault()).format(Date(createdAt))
    }
}

private fun notificationSectionTitle(createdAt: Long): String {
    if (createdAt <= 0L) return "Earlier"

    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = createdAt }

    return when {
        isSameDay(today, target) -> "Today"
        isYesterday(today, target) -> "Yesterday"
        else -> "Earlier"
    }
}

private fun isSameDay(first: Calendar, second: Calendar): Boolean {
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, target: Calendar): Boolean {
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(yesterday, target)
}
