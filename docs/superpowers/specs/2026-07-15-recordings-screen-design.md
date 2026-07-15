# Recordings Screen Design

## Overview

Aggiunta di una schermata dedicata alla gestione delle registrazioni audio all'interno dell'app ShizuCallRecorder.
La schermata permette all'utente di visualizzare, riprodurre, rinominare, condividere ed eliminare le registrazioni salvate nella cartella SAF configurata.

---

## Architettura & Navigazione

### Approccio scelto
**Screen dedicata con ViewModel + SAF** — segue esattamente i pattern già usati nel progetto.

### Nuovi file
- `ui/screens/RecordingsScreen.kt` — Composable principale
- `ui/viewmodels/RecordingsViewModel.kt` — ViewModel con StateFlow

### File modificati
- `AppNavigationScreen.kt` — aggiunge `AppScreen.Recordings` all'enum e gestisce il nuovo stato
- `ui/screens/SettingsScreen.kt` — aggiunge pulsante/card che chiama `onOpenRecordings: () -> Unit`

### Flusso di navigazione
```
SettingsScreen
  └─ [pulsante "Registrazioni"] → AppScreen.Recordings
        └─ RecordingsScreen
              └─ [← back] → AppScreen.Settings
```

La transizione usa l'animazione `slideInHorizontally + fadeIn` già esistente in `AppNavigationScreen`.

### Entry point
Un pulsante/card visibile nella `SettingsScreen` (la schermata principale post-onboarding).
`SettingsScreen` riceve un callback `onOpenRecordings: () -> Unit` che viene passato da `AppNavigationScreen`.

---

## Data Model

### `RecordingFile`
```kotlin
data class RecordingFile(
    val uri: Uri,           // URI SAF del file
    val name: String,       // nome file senza estensione
    val extension: String,  // es. "opus", "ogg"
    val sizeBytes: Long,
    val lastModified: Long, // epoch ms — usato per ordinamento e display
    val durationMs: Long?,  // null se non ricavabile (MediaMetadataRetriever)
    val direction: CallDirection? // inferita dal pattern "in"/"out" nel nome file
)
```

### `RecordingsUiState`
```kotlin
data class RecordingsUiState(
    val recordings: List<RecordingFile> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedUris: Set<Uri> = emptySet(),
    val errorMessage: String? = null,
    val noFolderConfigured: Boolean = false
)
```
`filteredRecordings` è una proprietà derivata nel ViewModel (applica `searchQuery` sulla lista).

---

## ViewModel — `RecordingsViewModel`

### Sorgente dati
- Legge la cartella via `DocumentFile.fromTreeUri(context, recordingFolderUri)`
- URI da `AppPreferences.getRecordingFolderUri()`
- Se URI è `null` → `noFolderConfigured = true`

### Operazioni
| Metodo | Dettaglio |
|---|---|
| `loadRecordings()` | Legge cartella SAF, ordina per `lastModified` desc |
| `deleteRecording(uri)` | `DocumentFile.delete()`, poi `loadRecordings()` |
| `deleteSelected()` | Elimina tutti gli URI in `selectedUris`, poi reload |
| `renameRecording(uri, newName)` | `DocumentFile.renameTo(newName + ext)`, poi reload |
| `toggleSelection(uri)` | Aggiunge/rimuove da `selectedUris` |
| `clearSelection()` | Svuota `selectedUris` |
| `setSearchQuery(q)` | Aggiorna `searchQuery`, filtra lista |

La durata viene letta con `MediaMetadataRetriever` su `Dispatchers.IO`, in parallelo al caricamento della lista.

---

## UI — `RecordingsScreen`

### Layout generale
```
┌─────────────────────────────────────┐
│  ← Registrazioni         [🗑 multi] │  ← LargeTopAppBar
│  🔍 Cerca registrazioni...          │  ← SearchBar (sempre visibile)
├─────────────────────────────────────┤
│  [📞↓] 2026-07-15 · 14:32          │
│  20260715_143200_in_+39...    [⋮]  │  ← ListItem con menu contestuale
│  2:34 · 1.2 MB                     │
├─────────────────────────────────────┤
│  [📞↑] 2026-07-14 · 09:10          │
│  20260714_091000_out_+39...   [⋮]  │
│  5:12 · 3.1 MB                     │
└─────────────────────────────────────┘
```

### Interazioni per item

| Gesto | Azione |
|---|---|
| Tap singolo | Apre mini media player (BottomSheet) |
| Tap lungo | Entra in modalità multi-selezione |
| Swipe ← (EndToStart) | **Elimina** — sfondo rosso + icona 🗑, poi dialog di conferma |
| Swipe → (StartToEnd) | **Rinomina** — sfondo blu/primario + icona ✏️, apre dialog |

### Menu contestuale ⋮ (modalità normale)
- Rinomina
- Condividi
- Elimina

### Modalità multi-selezione
- TopAppBar mostra `[X selezionate]` + icone **Condividi** e **Elimina** nella barra
- Tap su item toglie/aggiunge alla selezione (checkbox visibile)
- Tasto back esce dalla modalità selezione (senza eliminare)

### Dialogs
| Dialog | Trigger |
|---|---|
| Conferma eliminazione singola | Swipe sx o menu ⋮ > Elimina |
| Conferma eliminazione multipla | Elimina in modalità multi-selezione ("Eliminare X registrazioni?") |
| Rinomina | Swipe dx o menu ⋮ > Rinomina — AlertDialog con OutlinedTextField pre-compilato (senza estensione) |

### Empty States
| Stato | UI |
|---|---|
| Nessun file trovato | Illustrazione + "Nessuna registrazione trovata" |
| Cartella non configurata | Testo + pulsante "Vai alle impostazioni" |

---

## Media Player (BottomSheet)

Usa `MediaPlayer` Android standard (nessuna libreria extra).

### Contenuto
- Nome file registrazione
- Durata totale
- Slider di seek
- Pulsante play/pausa
- Pulsanti skip ±15s

### Lifecycle
- Si chiude automaticamente al `onNavigateBack`
- Si ferma se un altro item viene riprodotto (un solo player attivo)

---

## Error Handling & Edge Cases

| Scenario | Comportamento |
|---|---|
| URI cartella non valido / revocato | Messaggio di errore + pulsante "Vai alle impostazioni" |
| File eliminato esternamente durante l'uso | Reload automatico al resume, nessun crash |
| Rename fallisce (nome già esistente o caratteri non validi) | Snackbar di errore, dialog rimane aperta |
| Cartella vuota | Empty state con illustrazione |
| Nessuna cartella configurata | Stato speciale con CTA "Vai alle impostazioni" |
| MediaPlayer errore (formato non supportato) | Snackbar di errore, player non si apre |
| Permesso SAF revocato a runtime | `getRecordingFolderUri()` → null → stato "cartella non configurata" |

---

## Dipendenze

Nessuna nuova libreria richiesta. Tutto usa API già disponibili nel progetto:
- `DocumentFile` (androidx.documentfile)
- `MediaPlayer` (android.media)
- `MediaMetadataRetriever` (android.media)
- `SwipeToDismissBox` (Material3, già in uso nel progetto di riferimento)
- Jetpack Compose + Material3 (già in uso)
