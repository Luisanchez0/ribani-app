package com.ribani.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ribani.app.ui.theme.FondoBarraNavegacion
import com.ribani.app.ui.theme.IconoActivoBarra
import com.ribani.app.ui.theme.IconoInactivoBarra


@Composable
fun BottomNavigationBar(
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onMedicationClick: () -> Unit,
    onLocationClick: () -> Unit,
    onContactsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FondoBarraNavegacion
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationItem(
                iconIdle = Icons.Outlined.Home,
                iconSelected = Icons.Filled.Home,
                text = "Inicio",
                selected = currentRoute == "home",
                onClick = onHomeClick,
                modifier = Modifier.weight(1f)
            )
            NavigationItem(
                iconIdle = Icons.Outlined.Medication,
                iconSelected = Icons.Filled.Medication,
                text = "Medicinas",
                selected = currentRoute?.startsWith("medication") == true,
                onClick = onMedicationClick,
                modifier = Modifier.weight(1f)
            )
            NavigationItem(
                iconIdle = Icons.Outlined.LocationOn,
                iconSelected = Icons.Filled.LocationOn,
                text = "Mi ubicación",
                selected = currentRoute == "location",
                onClick = onLocationClick,
                modifier = Modifier.weight(1f)
            )
            NavigationItem(
                iconIdle = Icons.Outlined.Person,
                iconSelected = Icons.Filled.Person,
                text = "Contactos",
                selected = currentRoute == "contacts",
                onClick = onContactsClick,
                modifier = Modifier.weight(1f)
            )
            NavigationItem(
                iconIdle = Icons.Outlined.Settings,
                iconSelected = Icons.Filled.Settings,
                text = "Ajustes",
                selected = currentRoute?.startsWith("settings") == true,
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavigationItem(
    iconIdle: ImageVector,
    iconSelected: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) IconoActivoBarra else IconoInactivoBarra
    Column(
        modifier = modifier.heightIn(min = 64.dp).selectable(
            selected = selected,
            role = Role.Tab,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = if (selected) {
                        IconoActivoBarra.copy(alpha = 0.10f)
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selected) iconSelected else iconIdle,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = tint,
                fontWeight = if (selected) {
                    androidx.compose.ui.text.font.FontWeight.SemiBold
                } else {
                    androidx.compose.ui.text.font.FontWeight.Normal
                },
                maxLines = 2
            )
        }
    }
}
