package com.example.wtfff.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wtfff.R;
import com.example.wtfff.model.BrainstormSession;
import com.example.wtfff.model.CreativeSkill;
import com.example.wtfff.model.IdeaCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IdeaCardAdapter extends RecyclerView.Adapter<IdeaCardAdapter.CardViewHolder> {

    public interface Listener {
        void onQuickBranch(IdeaCard card);
        void onPickSkillBranch(IdeaCard card);
        void onToggleCollapse(IdeaCard card);
        void onToggleStar(IdeaCard card);
        void onToggleSelectMashup(IdeaCard card);
        void onCopyCard(IdeaCard card);
        void onBanishCard(IdeaCard card);
    }

    private final Listener listener;
    private final List<IdeaCard> visibleCards = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private BrainstormSession session;
    private CreativeSkill activeSkill;

    public IdeaCardAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitData(BrainstormSession session,
                           List<IdeaCard> orderedVisible,
                           CreativeSkill activeSkill,
                           Set<String> selectedMashupIds) {
        this.session = session;
        this.activeSkill = activeSkill;
        this.visibleCards.clear();
        if (orderedVisible != null) {
            this.visibleCards.addAll(orderedVisible);
        }
        this.selectedIds.clear();
        if (selectedMashupIds != null) {
            this.selectedIds.addAll(selectedMashupIds);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_idea_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        IdeaCard card = visibleCards.get(position);
        boolean isSelected = selectedIds.contains(card.id);

        holder.branchConnector.setBranchSpec(card.depth, card.accentColorHex);
        holder.cardContainer.setBackgroundResource(
                isSelected ? R.drawable.bg_px_cartridge_selected : R.drawable.bg_px_cartridge
        );

        holder.viewAccentPip.setBackgroundColor(card.accentColorHex);
        holder.tvSkillBadge.setText(card.skillBadge);
        holder.tvSkillBadge.setTextColor(card.accentColorHex);

        int subtreeCount = session != null ? session.countSubtree(card.id) : 0;
        if (subtreeCount > 0) {
            holder.btnCollapseToggle.setVisibility(View.VISIBLE);
            String prefix = card.collapsed ? "[+" : "[-";
            holder.btnCollapseToggle.setText(prefix + subtreeCount + "]");
            holder.btnCollapseToggle.setTextColor(card.collapsed ? 0xFFFFB800 : 0xFF8DA4C7);
            holder.btnCollapseToggle.setOnClickListener(v -> listener.onToggleCollapse(card));
        } else {
            holder.btnCollapseToggle.setVisibility(View.GONE);
        }

        holder.btnSelectMashup.setText(isSelected ? "[✓ SPLICED]" : "[+SPLICE]");
        holder.btnSelectMashup.setTextColor(isSelected ? 0xFFFF2E93 : 0xFF8DA4C7);
        holder.btnSelectMashup.setBackgroundResource(
                isSelected ? R.drawable.bg_px_btn_magenta : R.drawable.bg_px_chip_idle
        );
        holder.btnSelectMashup.setOnClickListener(v -> listener.onToggleSelectMashup(card));

        ImageViewCompat.setImageTintList(
                holder.btnStar,
                ColorStateList.valueOf(card.starred ? 0xFFFFB800 : 0xFF516587)
        );
        holder.btnStar.setOnClickListener(v -> listener.onToggleStar(card));

        holder.tvCardTitle.setText(card.title);
        holder.tvCardSummary.setText(card.summary);

        if (card.rationale != null && !card.rationale.trim().isEmpty()) {
            holder.tvCardRationale.setVisibility(View.VISIBLE);
            holder.tvCardRationale.setText("// WHY: " + card.rationale.trim());
        } else {
            holder.tvCardRationale.setVisibility(View.GONE);
        }

        if (card.tags != null && !card.tags.isEmpty()) {
            holder.tvCardTags.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < card.tags.size(); i++) {
                if (i > 0) sb.append("   ");
                String t = card.tags.get(i);
                sb.append(t.startsWith("#") ? t : ("#" + t));
            }
            holder.tvCardTags.setText(sb.toString());
        } else {
            holder.tvCardTags.setVisibility(View.GONE);
        }

        String quickSkillLabel = activeSkill != null ? ("BRANCH: " + activeSkill.badge) : "BRANCH";
        holder.tvBranchQuickLabel.setText(quickSkillLabel);

        holder.btnBranchQuick.setOnClickListener(v -> listener.onQuickBranch(card));
        holder.btnBranchPickSkill.setOnClickListener(v -> listener.onPickSkillBranch(card));
        holder.btnCopyCard.setOnClickListener(v -> listener.onCopyCard(card));
        holder.btnBanishCard.setOnClickListener(v -> listener.onBanishCard(card));
    }

    @Override
    public int getItemCount() {
        return visibleCards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        final PixelBranchConnectorView branchConnector;
        final LinearLayout cardContainer;
        final View viewAccentPip;
        final TextView tvSkillBadge;
        final TextView btnCollapseToggle;
        final TextView btnSelectMashup;
        final ImageButton btnStar;
        final TextView tvCardTitle;
        final TextView tvCardSummary;
        final TextView tvCardRationale;
        final TextView tvCardTags;
        final LinearLayout btnBranchQuick;
        final TextView tvBranchQuickLabel;
        final TextView btnBranchPickSkill;
        final TextView btnCopyCard;
        final LinearLayout btnBanishCard;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            branchConnector = itemView.findViewById(R.id.branchConnector);
            cardContainer = itemView.findViewById(R.id.cardContainer);
            viewAccentPip = itemView.findViewById(R.id.viewAccentPip);
            tvSkillBadge = itemView.findViewById(R.id.tvSkillBadge);
            btnCollapseToggle = itemView.findViewById(R.id.btnCollapseToggle);
            btnSelectMashup = itemView.findViewById(R.id.btnSelectMashup);
            btnStar = itemView.findViewById(R.id.btnStar);
            tvCardTitle = itemView.findViewById(R.id.tvCardTitle);
            tvCardSummary = itemView.findViewById(R.id.tvCardSummary);
            tvCardRationale = itemView.findViewById(R.id.tvCardRationale);
            tvCardTags = itemView.findViewById(R.id.tvCardTags);
            btnBranchQuick = itemView.findViewById(R.id.btnBranchQuick);
            tvBranchQuickLabel = itemView.findViewById(R.id.tvBranchQuickLabel);
            btnBranchPickSkill = itemView.findViewById(R.id.btnBranchPickSkill);
            btnCopyCard = itemView.findViewById(R.id.btnCopyCard);
            btnBanishCard = itemView.findViewById(R.id.btnBanishCard);
        }
    }
}
