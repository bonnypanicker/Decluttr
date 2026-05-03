# Wishlist Notes Feature — Implementation Plan

## Overview

Notes already exist in the data layer (`WishlistApp.notes`, `WishlistEntity.notes`, `WishlistDao`,
`WishlistRepository.updateNotes`, `WishlistRepositoryImpl`, `WishlistViewModel.updateNotes`).
The display layer (`WishlistAdapter.notesView`) also exists.

**What this plan adds:**
- Notes edit button in `item_wishlist.xml` beside delete, with a lock state for free users
- `WishlistNotesBottomSheet` — inline edit sheet with char counter, save/cancel
- Premium gate throughout: lock icon + paywall on tap for free users
- Optional notes capture in the Play Store share popup (premium only)
- `BillingViewModel` wired into `WishlistFragment` and `ShareReceiverActivity`

Nothing in the data/repository/ViewModel layer needs to change.

---

## 1. `item_wishlist.xml` — Add Notes Button

Add a notes `ImageButton` between the text block and the delete button. The button shows
`ic_edit_note` for premium users and `ic_lock` for free users. Both share the same ID;
the icon and tint change at bind time.

```xml
<!-- Replace the existing action buttons section (from <ImageButton id="btn_delete"> onwards) -->

<!-- Notes button -->
<ImageButton
    android:id="@+id/btn_notes"
    android:layout_width="36dp"
    android:layout_height="36dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:src="@drawable/ic_edit_note"
    android:contentDescription="Edit note"
    android:padding="6dp" />

<!-- Delete button (existing, unchanged) -->
<ImageButton
    android:id="@+id/btn_delete"
    android:layout_width="40dp"
    android:layout_height="40dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:src="@drawable/ic_delete"
    android:tint="?attr/colorError" />
```

The notes button sits in the same horizontal `LinearLayout` as the delete button. No layout
changes to the card's outer structure are needed.

---

## 2. `WishlistAdapter.kt` — Wire Notes Button + Premium State

### Constructor change

Add two new callbacks. Pass `isPremium: Boolean` as an adapter-level flag updated via a
dedicated method (same pattern as `ArchivedAppsAdapter.setListMode`).

```kotlin
class WishlistAdapter(
    private val onDeleteClick: (WishlistApp) -> Unit,
    private val onPlayStoreClick: (WishlistApp) -> Unit,
    private val onNotesClick: (WishlistApp) -> Unit,     // ← NEW
    private val onUpgradeClick: () -> Unit,              // ← NEW (free user taps notes lock)
) : ListAdapter<WishlistApp, WishlistAdapter.ViewHolder>(WishlistDiffCallback()) {

    private var isPremium: Boolean = false

    /** Called from WishlistFragment when entitlement changes. */
    fun setPremium(premium: Boolean) {
        if (isPremium != premium) {
            isPremium = premium
            notifyItemRangeChanged(0, itemCount, PAYLOAD_PREMIUM_CHANGED)
        }
    }

    companion object {
        private const val PAYLOAD_PREMIUM_CHANGED = "premium_changed"
    }
```

Using `notifyItemRangeChanged` with a payload avoids full rebind/flicker when entitlement
changes (e.g. after purchase completes).

### ViewHolder — `bind()` and partial bind

```kotlin
inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val iconView: ImageView = itemView.findViewById(R.id.iv_icon)
    private val nameView: TextView = itemView.findViewById(R.id.tv_name)
    private val descView: TextView = itemView.findViewById(R.id.tv_desc)
    private val notesView: TextView = itemView.findViewById(R.id.tv_notes)
    private val btnNotes: ImageButton = itemView.findViewById(R.id.btn_notes)
    private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

    fun bind(app: WishlistApp) {
        nameView.text = app.name
        descView.text = app.description.ifBlank { "No description available" }

        // Notes text (unchanged from existing logic)
        if (app.notes.isNotBlank()) {
            notesView.text = app.notes
            notesView.visibility = View.VISIBLE
        } else {
            notesView.visibility = View.GONE
        }

        // Load icon (existing logic unchanged)
        loadIcon(app)

        bindNoteButton(app)

        btnDelete.setOnClickListener { onDeleteClick(app) }
        itemView.setOnClickListener { onPlayStoreClick(app) }
    }

    /**
     * Partial bind for premium state changes — only updates the notes button.
     * Called when payload == PAYLOAD_PREMIUM_CHANGED.
     */
    fun bindNoteButton(app: WishlistApp) {
        if (isPremium) {
            btnNotes.setImageResource(R.drawable.ic_edit_note)
            btnNotes.alpha = 1f
            btnNotes.contentDescription = if (app.notes.isBlank()) "Add note" else "Edit note"
            btnNotes.setColorFilter(
                MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            btnNotes.setOnClickListener { onNotesClick(app) }
        } else {
            btnNotes.setImageResource(R.drawable.ic_lock)
            btnNotes.alpha = 0.45f
            btnNotes.contentDescription = "Notes — Premium feature"
            btnNotes.setColorFilter(
                MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            btnNotes.setOnClickListener { onUpgradeClick() }
        }
    }
}
```

### Override `onBindViewHolder` with payload support

```kotlin
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
}

override fun onBindViewHolder(
    holder: ViewHolder,
    position: Int,
    payloads: MutableList<Any>
) {
    if (payloads.contains(PAYLOAD_PREMIUM_CHANGED)) {
        // Only rebind the notes button — everything else stays.
        holder.bindNoteButton(getItem(position))
    } else {
        super.onBindViewHolder(holder, position, payloads)
    }
}
```

---

## 3. `WishlistNotesBottomSheet.kt` — New File

Create at `presentation/screens/wishlist/WishlistNotesBottomSheet.kt`.

```kotlin
package com.tool.decluttr.presentation.screens.wishlist

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tool.decluttr.R
import com.tool.decluttr.domain.model.WishlistApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WishlistNotesBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PACKAGE_ID = "packageId"
        private const val MAX_NOTES_CHARS = 500

        fun newInstance(packageId: String): WishlistNotesBottomSheet =
            WishlistNotesBottomSheet().apply {
                arguments = bundleOf(ARG_PACKAGE_ID to packageId)
            }
    }

    private val viewModel: WishlistViewModel by viewModels()
    private var currentApp: WishlistApp? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_wishlist_notes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val packageId = arguments?.getString(ARG_PACKAGE_ID) ?: run { dismiss(); return }

        val ivIcon: ImageView = view.findViewById(R.id.iv_app_icon)
        val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
        val tvCharCount: TextView = view.findViewById(R.id.tv_char_count)
        val tilNotes: TextInputLayout = view.findViewById(R.id.til_notes)
        val etNotes: TextInputEditText = view.findViewById(R.id.et_notes)
        val btnSave: MaterialButton = view.findViewById(R.id.btn_save)
        val btnCancel: MaterialButton = view.findViewById(R.id.btn_cancel)

        etNotes.filters = arrayOf(InputFilter.LengthFilter(MAX_NOTES_CHARS))

        // Find the app from the live wishlist StateFlow snapshot
        val app = viewModel.wishlist.value.find { it.packageId == packageId }
        currentApp = app

        if (app != null) {
            tvAppName.text = app.name
            if (app.iconUrl.isNotBlank()) {
                ivIcon.load(app.iconUrl) {
                    crossfade(false)
                    transformations(RoundedCornersTransformation(radius = 18f))
                    placeholder(R.drawable.ic_app_placeholder)
                    error(R.drawable.ic_app_placeholder)
                }
            } else {
                runCatching {
                    ivIcon.setImageDrawable(
                        requireContext().packageManager.getApplicationIcon(app.packageId)
                    )
                }.onFailure { ivIcon.setImageResource(R.drawable.ic_app_placeholder) }
            }
            etNotes.setText(app.notes)
            etNotes.setSelection(app.notes.length)   // cursor at end
        }

        // Character counter
        fun updateCounter(length: Int) {
            tvCharCount.text = "$length / $MAX_NOTES_CHARS"
            val nearLimit = length >= MAX_NOTES_CHARS * 0.9
            tvCharCount.setTextColor(
                if (nearLimit)
                    requireContext().getColor(android.R.color.holo_orange_dark)
                else
                    com.google.android.material.color.MaterialColors.getColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant, 0
                    )
            )
        }
        updateCounter(app?.notes?.length ?: 0)

        etNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCounter(s?.length ?: 0)
                // Enable save only if content actually changed
                val original = currentApp?.notes ?: ""
                btnSave.isEnabled = s.toString() != original
            }
        })

        // Initial save button state
        btnSave.isEnabled = false

        btnSave.setOnClickListener {
            val notes = etNotes.text?.toString()?.trim() ?: ""
            viewModel.updateNotes(packageId, notes)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
    }
}
```

**Why `viewModel.wishlist.value` instead of a coroutine observe:**
The bottom sheet is modal — it opens on an existing item. Reading from the already-hot
`StateFlow.value` synchronously is safe and avoids flickering a loading state for something
the list already has.

---

## 4. `bottom_sheet_wishlist_notes.xml` — New Layout File

Create at `res/layout/bottom_sheet_wishlist_notes.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="24dp"
    android:paddingTop="16dp"
    android:paddingBottom="24dp">

    <!-- Drag handle -->
    <View
        android:layout_width="32dp"
        android:layout_height="4dp"
        android:layout_gravity="center_horizontal"
        android:layout_marginBottom="20dp"
        android:background="@drawable/bg_bottom_sheet_handle" />

    <!-- App identity row -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="20dp">

        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/iv_app_icon"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:scaleType="centerCrop"
            app:shapeAppearanceOverlay="@style/SquircleIconShape"
            android:background="@drawable/ic_app_placeholder" />

        <TextView
            android:id="@+id/tv_app_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:textAppearance="?attr/textAppearanceTitleMedium"
            android:maxLines="1"
            android:ellipsize="end" />
    </LinearLayout>

    <!-- Notes input -->
    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/til_notes"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Add a note…"
        app:boxBackgroundMode="outline"
        app:boxCornerRadiusTopStart="12dp"
        app:boxCornerRadiusTopEnd="12dp"
        app:boxCornerRadiusBottomStart="12dp"
        app:boxCornerRadiusBottomEnd="12dp"
        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/et_notes"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textMultiLine|textCapSentences"
            android:minLines="3"
            android:maxLines="5"
            android:gravity="top|start"
            android:paddingTop="12dp"
            android:paddingBottom="12dp" />

    </com.google.android.material.textfield.TextInputLayout>

    <!-- Character counter -->
    <TextView
        android:id="@+id/tv_char_count"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="6dp"
        android:gravity="end"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorOnSurfaceVariant"
        android:text="0 / 500" />

    <!-- Actions -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="20dp"
        android:gravity="end">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_cancel"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginEnd="8dp"
            android:text="Cancel"
            style="@style/Widget.Material3.Button.TextButton" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_save"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Save"
            android:enabled="false" />

    </LinearLayout>
</LinearLayout>
```

---

## 5. `WishlistFragment.kt` — Wire Premium + Notes Callback

### Add `BillingViewModel`

```kotlin
// Add alongside existing viewModel declaration
private val billingViewModel: BillingViewModel by activityViewModels()
```

`activityViewModels()` reuses the same `BillingViewModel` instance already alive from the
main dashboard scope — no extra overhead.

### Update adapter construction

```kotlin
val adapter = WishlistAdapter(
    onDeleteClick = { app ->
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove from Wishlist?")
            .setMessage("Are you sure you want to remove ${app.name} from your wishlist?")
            .setPositiveButton("Remove") { _, _ -> viewModel.remove(app.packageId) }
            .setNegativeButton("Cancel", null)
            .show()
    },
    onPlayStoreClick = { app ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(app.playStoreUrl)
            setPackage("com.android.vending")
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.playStoreUrl)))
        }
    },
    onNotesClick = { app ->                                          // ← NEW
        val tag = "WishlistNotesSheet"
        if (parentFragmentManager.findFragmentByTag(tag) == null) {
            WishlistNotesBottomSheet.newInstance(app.packageId)
                .show(parentFragmentManager, tag)
        }
    },
    onUpgradeClick = {                                               // ← NEW
        val tag = "PaywallBottomSheet"
        if (parentFragmentManager.findFragmentByTag(tag) == null) {
            PaywallBottomSheet.newInstance(reason = "wishlist_notes")
                .show(parentFragmentManager, tag)
        }
    }
)
```

### Observe entitlement and push to adapter

Add inside the `repeatOnLifecycle` block (alongside the existing `wishlist.collect` observer):

```kotlin
launch {
    billingViewModel.entitlementState.collect { entitlement ->
        adapter.setPremium(entitlement.isPremium)
    }
}
```

This ensures the adapter's premium state stays live — e.g. if a user purchases while
the wishlist is open, the lock icons flip to edit icons without any navigation.

---

## 6. Share Popup Notes — `dialog_wishlist_confirm.xml` + `ShareReceiverActivity`

### `dialog_wishlist_confirm.xml` — Add notes section

Extend the existing layout with a notes `TextInputLayout` beneath the description. It renders
as a locked field for free users and an editable field for premium users. The lock state is
driven by code, not by a separate layout.

```xml
<!-- Append inside the existing inner LinearLayout, after tv_app_desc -->

<!-- Divider -->
<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:layout_marginTop="12dp"
    android:layout_marginBottom="12dp"
    android:background="?attr/colorOutlineVariant" />

<!-- Notes row: locked for free, editable for premium -->
<com.google.android.material.textfield.TextInputLayout
    android:id="@+id/til_share_notes"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Add a note (optional)"
    app:boxBackgroundMode="outline"
    app:boxCornerRadiusTopStart="10dp"
    app:boxCornerRadiusTopEnd="10dp"
    app:boxCornerRadiusBottomStart="10dp"
    app:boxCornerRadiusBottomEnd="10dp"
    app:endIconMode="none"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/et_share_notes"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textMultiLine|textCapSentences"
        android:maxLines="3"
        android:gravity="top|start" />

</com.google.android.material.textfield.TextInputLayout>

<!-- Locked state caption — visible for free users -->
<LinearLayout
    android:id="@+id/notes_lock_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:visibility="gone">

    <ImageView
        android:layout_width="14dp"
        android:layout_height="14dp"
        android:src="@drawable/ic_lock"
        android:alpha="0.55"
        app:tint="?attr/colorOnSurfaceVariant" />

    <TextView
        android:id="@+id/tv_notes_lock_label"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="4dp"
        android:text="Notes — Premium only"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="?attr/colorOnSurfaceVariant"
        android:alpha="0.7" />
</LinearLayout>
```

### `ShareReceiverActivity.kt` — Read premium, apply notes field, pass to `add()`

Add `BillingViewModel` injection and wire up the notes field in `showConfirmDialog()`.

#### Add ViewModel

```kotlin
// In the class body, alongside existing viewModel
private val billingViewModel: BillingViewModel by viewModels()
```

#### Update `showConfirmDialog()` — notes wiring

Replace the current `showConfirmDialog` with the version below. All existing logic is
unchanged; only the notes field block is new.

```kotlin
private fun showConfirmDialog(info: PlayStoreAppInfo, playStoreUrl: String) {
    val view = layoutInflater.inflate(R.layout.dialog_wishlist_confirm, null)

    // Existing views (unchanged)
    val iconView     = view.findViewById<ImageView>(R.id.iv_app_icon)
    val nameView     = view.findViewById<TextView>(R.id.tv_app_name)
    val descView     = view.findViewById<TextView>(R.id.tv_app_desc)

    // Notes views (new)
    val tilNotes     = view.findViewById<TextInputLayout>(R.id.til_share_notes)
    val etNotes      = view.findViewById<TextInputEditText>(R.id.et_share_notes)
    val lockRow      = view.findViewById<View>(R.id.notes_lock_row)

    val isPremium = billingViewModel.entitlementState.value.isPremium

    if (isPremium) {
        // Premium: fully editable
        etNotes.isEnabled = true
        etNotes.isFocusable = true
        etNotes.isFocusableInTouchMode = true
        etNotes.filters = arrayOf(InputFilter.LengthFilter(500))
        lockRow.visibility = View.GONE
        tilNotes.hint = "Add a note (optional)"
    } else {
        // Free: field visible but disabled; lock caption shown; tap → upgrade dialog
        etNotes.isEnabled = false
        etNotes.isFocusable = false
        etNotes.hint = ""
        tilNotes.hint = "Notes (Premium)"
        tilNotes.alpha = 0.45f
        lockRow.visibility = View.VISIBLE
        tilNotes.setOnClickListener { showSharePaywallDialog() }
        lockRow.setOnClickListener { showSharePaywallDialog() }
    }

    // Existing icon/name/desc population (unchanged)
    nameView.text = info.name
    descView.text = info.description.ifBlank { "No description available" }
    // ... icon loading (unchanged) ...

    MaterialAlertDialogBuilder(this)
        .setView(view)
        .setPositiveButton("Add to Wishlist") { _, _ ->
            lifecycleScope.launch {
                if (!ensureLoggedInOrRedirect()) return@launch
                val notes = if (isPremium) etNotes.text?.toString()?.trim() ?: "" else ""
                runCatching {
                    withContext(NonCancellable) {
                        viewModel.add(
                            WishlistApp(
                                packageId    = info.packageId,
                                name         = info.name,
                                iconUrl      = info.iconUrl,
                                description  = info.description,
                                playStoreUrl = playStoreUrl,
                                category     = info.category,
                                notes        = notes                // ← passes notes
                            )
                        )
                    }
                }.onFailure { error ->
                    Toast.makeText(this@ShareReceiverActivity,
                        "Couldn't save right now. Please try again.", Toast.LENGTH_SHORT).show()
                }.onSuccess {
                    Toast.makeText(this@ShareReceiverActivity,
                        "${info.name} added to wishlist", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
        .setNegativeButton("Cancel") { _, _ -> finish() }
        .setOnCancelListener { finish() }
        .show()
}
```

#### Add `showSharePaywallDialog()` helper

`ShareReceiverActivity` is an `Activity`, not a `Fragment`, so `PaywallBottomSheet` (a
`BottomSheetDialogFragment`) cannot be shown directly. Instead, show a lightweight
`MaterialAlertDialog` that redirects to the main app.

```kotlin
private fun showSharePaywallDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Premium Feature")
        .setMessage("Notes on wishlist items is a Decluttr Pro feature. Upgrade to add notes when saving apps from Play Store.")
        .setPositiveButton("Get Pro") { _, _ ->
            // Open the main app; the user can upgrade from there.
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("open_paywall", true)        // MainActivity reads this to auto-show paywall
                }
            )
            finish()
        }
        .setNegativeButton("Not now", null)
        .show()
}
```

Add `open_paywall` handling in `MainActivity.onCreate()` / `onNewIntent()`:

```kotlin
// In MainActivity, after setContentView:
if (intent.getBooleanExtra("open_paywall", false)) {
    PaywallBottomSheet.newInstance(reason = "wishlist_notes_share")
        .show(supportFragmentManager, "PaywallBottomSheet")
}
```

---

## 7. Import Reference (files that need new imports)

### `WishlistFragment.kt`
```kotlin
import androidx.fragment.app.activityViewModels
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
import com.tool.decluttr.presentation.screens.billing.PaywallBottomSheet
```

### `WishlistAdapter.kt`
```kotlin
import com.google.android.material.color.MaterialColors
```

### `ShareReceiverActivity.kt`
```kotlin
import android.text.InputFilter
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tool.decluttr.presentation.screens.billing.BillingViewModel
```

---

## 8. File Change Summary

| File | Status | What changes |
|------|--------|-------------|
| `res/layout/item_wishlist.xml` | Modify | Add `btn_notes` ImageButton beside delete |
| `WishlistAdapter.kt` | Modify | Add `onNotesClick`, `onUpgradeClick`, `setPremium()`, partial bind |
| `WishlistNotesBottomSheet.kt` | **New** | Edit-note bottom sheet with char counter |
| `res/layout/bottom_sheet_wishlist_notes.xml` | **New** | Layout for notes sheet |
| `WishlistFragment.kt` | Modify | Add `BillingViewModel`, pass new adapter callbacks, observe entitlement |
| `res/layout/dialog_wishlist_confirm.xml` | Modify | Add notes `TextInputLayout` + lock row |
| `ShareReceiverActivity.kt` | Modify | Add `BillingViewModel`, wire notes field, `showSharePaywallDialog()` |
| `MainActivity.kt` | Modify | Handle `open_paywall` intent extra |

**No changes needed:**
`WishlistApp`, `WishlistEntity`, `WishlistDao`, `WishlistRepository`,
`WishlistRepositoryImpl`, `WishlistViewModel` — the data layer is already complete.

---

## 9. Edge Cases and Verification Checklist

| Scenario | Expected behaviour |
|----------|--------------------|
| Free user taps notes button in list | Lock icon visible; `onUpgradeClick` → `PaywallBottomSheet` |
| Premium user taps notes button, app has no notes | Sheet opens, `et_notes` is empty, save is disabled until typing |
| Premium user taps notes button, app has existing notes | Sheet opens pre-filled, cursor at end, save disabled until content changes |
| User saves empty string over existing notes | `updateNotes(packageId, "")` → `tv_notes` hides in list (existing `notesView.visibility` logic handles this) |
| User purchases Pro while wishlist is open | `entitlementState.collect` fires → `adapter.setPremium(true)` → payload rebind replaces lock icons with edit icons with no list flicker |
| User opens share popup as free user | Notes field visible but disabled + lock caption; tap lock → `showSharePaywallDialog()` |
| User opens share popup as premium user | Notes field fully editable; notes saved alongside app on confirm |
| Notes at 500 char limit | `InputFilter.LengthFilter` prevents further input; counter turns orange at ~90% |
| `WishlistNotesBottomSheet` opened for deleted item | `viewModel.wishlist.value.find { }` returns null → `dismiss()` immediately (safe) |
| Share popup dismissed without confirming | `finish()` in cancel/cancel listeners; no notes written |
| Rotation while notes sheet is open | Fragment manager restores the sheet; `viewModel.wishlist.value` is re-read from the same hot `StateFlow` |
| Notes sync to Firestore | `updateNotes()` already calls `enqueueUpsert()` in `WishlistRepositoryImpl`; no change needed |
