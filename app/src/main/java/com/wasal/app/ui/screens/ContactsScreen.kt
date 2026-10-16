
package com.wasal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.model.Contact

private val ContactsBackground = Color(0xFF0B1020)
private val ContactsCard = Color(0xFF172342)
private val ContactsAccent = Color(0xFF6C8CFF)
private val ContactsText = Color(0xFFF4F6FF)
private val ContactsMuted = Color(0xFF9AA7C7)

@Composable
fun ContactsScreen(
    contacts: List<Contact> = emptyList(),
    onContactClick: (Contact) -> Unit = {},
    onAddContact: (String) -> Unit = {},
    openAddFormSignal: Int = 0
) {
    var searchText by remember { mutableStateOf("") }
    var wasalCode by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    LaunchedEffect(openAddFormSignal) {
        if (openAddFormSignal > 0) {
            showAddForm = true
        }
    }

    val filteredContacts = contacts.filter {
        it.name.contains(searchText, ignoreCase = true) ||
            it.wasalCode.contains(searchText, ignoreCase = true)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ContactsBackground)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                singleLine = true,
                placeholder = {
                    Text("ابحث عن جهة اتصال", color = ContactsMuted)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = ContactsAccent
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ContactsText,
                    unfocusedTextColor = ContactsText,
                    focusedBorderColor = ContactsAccent,
                    unfocusedBorderColor = ContactsCard,
                    cursorColor = ContactsAccent,
                    focusedContainerColor = ContactsCard,
                    unfocusedContainerColor = ContactsCard
                )
            )

            Button(
                onClick = { showAddForm = !showAddForm },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ContactsAccent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (showAddForm) {
                        "إخفاء إضافة جهة اتصال"
                    } else {
                        "إضافة جهة اتصال"
                    }
                )
            }

            if (showAddForm) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            ContactsCard,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        "أدخل رمز وصال الخاص بالشخص",
                        color = ContactsText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(Modifier.size(8.dp))

                    OutlinedTextField(
                        value = wasalCode,
                        onValueChange = { value ->
                            wasalCode = value
                                .filter { it in 'A'..'Z' ||
                                    it in 'a'..'z' ||
                                    it in '0'..'9' }
                                .take(15)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text("رمز وصال", color = ContactsMuted)
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ContactsText,
                            unfocusedTextColor = ContactsText,
                            focusedBorderColor = ContactsAccent,
                            unfocusedBorderColor = ContactsMuted,
                            cursorColor = ContactsAccent,
                            focusedContainerColor = ContactsBackground,
                            unfocusedContainerColor = ContactsBackground
                        )
                    )

                    Spacer(Modifier.size(10.dp))

                    Button(
                        onClick = {
                            val code = wasalCode.trim()
                            if (code.matches(Regex("^[A-Za-z0-9]{15}$"))) {
                                onAddContact(code)
                                wasalCode = ""
                                showAddForm = false
                            }
                        },
                        enabled = wasalCode.matches(
                            Regex("^[A-Za-z0-9]{15}$")
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ContactsAccent
                        )
                    ) {
                        Text("متابعة")
                    }

                    Text(
                        "يتكون رمز وصال من 15 حرفًا إنجليزيًا أو رقمًا.",
                        color = ContactsMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.size(14.dp))

            Text(
                "جهات الاتصال (${filteredContacts.size})",
                color = ContactsText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.size(10.dp))

            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .background(ContactsCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = ContactsAccent,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(Modifier.size(14.dp))

                        Text(
                            if (searchText.isBlank()) {
                                "لا توجد جهات اتصال بعد"
                            } else {
                                "لم يتم العثور على نتائج"
                            },
                            color = ContactsText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(Modifier.size(6.dp))

                        Text(
                            "يمكنك إضافة جهة اتصال باستخدام رمز وصال.",
                            color = ContactsMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    ContactsCard,
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { onContactClick(contact) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        ContactsAccent.copy(alpha = 0.18f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    contact.name.trim().take(1).ifBlank { "و" },
                                    color = ContactsAccent,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.size(12.dp))

                            Column {
                                Text(
                                    contact.name,
                                    color = ContactsText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (contact.wasalCode.isNotBlank()) {
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        "رمز وصال: ${contact.wasalCode}",
                                        color = ContactsMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
