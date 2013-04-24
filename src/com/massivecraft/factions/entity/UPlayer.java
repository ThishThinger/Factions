package com.massivecraft.factions.entity;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import com.massivecraft.factions.EconomyParticipator;
import com.massivecraft.factions.FFlag;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.Lang;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.RelationParticipator;
import com.massivecraft.factions.event.FactionsEventChunkChange;
import com.massivecraft.factions.event.FactionsEventMembershipChange;
import com.massivecraft.factions.event.FactionsEventMembershipChange.MembershipChangeReason;
import com.massivecraft.factions.util.RelationUtil;
import com.massivecraft.mcore.mixin.Mixin;
import com.massivecraft.mcore.ps.PS;
import com.massivecraft.mcore.ps.PSFormatSlug;
import com.massivecraft.mcore.store.SenderEntity;
import com.massivecraft.mcore.util.MUtil;
import com.massivecraft.mcore.util.SenderUtil;


public class UPlayer extends SenderEntity<UPlayer> implements EconomyParticipator
{
	// -------------------------------------------- //
	// META
	// -------------------------------------------- //
	
	public static UPlayer get(Object oid)
	{
		return UPlayerColls.get().get2(oid);
	}
	
	// -------------------------------------------- //
	// OVERRIDE: ENTITY
	// -------------------------------------------- //
	
	@Override
	public UPlayer load(UPlayer that)
	{
		this.setFactionId(that.factionId);
		this.setRole(that.role);
		this.setTitle(that.title);
		this.setPower(that.power);
		
		return this;
	}
	
	@Override
	public boolean isDefault()
	{
		if (this.hasFaction()) return false;
		// Role means nothing without a faction.
		// Title means nothing without a faction.
		if (this.getPowerRounded() != (int) Math.round(UConf.get(this).defaultPlayerPower)) return false;
		
		return true;
	}
	
	// -------------------------------------------- //
	// FIELDS: RAW
	// -------------------------------------------- //
	// In this section of the source code we place the field declarations only.
	// Each field has it's own section further down since just the getter and setter logic takes up quite some place.
	
	// This is a foreign key.
	// Each player belong to a faction.
	// Null means default for the universe.
	private String factionId = null;
	
	// What role does the player have in the faction?
	// Null means default for the universe.
	private Rel role = null;
	
	// What title does the player have in the faction?
	// The title is just for fun. It's not connected to any game mechanic.
	// The player title is similar to the faction description.
	// 
	// Question: Can the title contain chat colors?
	// Answer: Yes but in such case the policy is that they already must be parsed using Txt.parse.
	//         If the title contains raw markup, such as "<white>" instead of "§f" it will not be parsed and "<white>" will be displayed.
	//
	// Null means the player has no title.
	private String title = null;
	
	// Each player has an individual power level.
	// The power level for online players is occasionally updated by a recurring task and the power should stay the same for offline players.
	// For that reason the value is to be considered correct when you pick it. Do not call the power update method.
	// Null means default for the universe.
	private Double power = null;
	
	// The id for the faction this uplayer is currently autoclaiming for.
	// NOTE: This field will not be saved to the database ever.
	// Null means the player isn't auto claiming.
	private transient Faction autoClaimFaction = null;
	public Faction getAutoClaimFaction() { return this.autoClaimFaction; }
	public void setAutoClaimFaction(Faction autoClaimFaction) { this.autoClaimFaction = autoClaimFaction; }
	
	// -------------------------------------------- //
	// FIELDS: MULTIVERSE PROXY
	// -------------------------------------------- //
	
	public boolean isMapAutoUpdating() { return MPlayer.get(this).isMapAutoUpdating(); }
	public void setMapAutoUpdating(boolean mapAutoUpdating) { MPlayer.get(this).setMapAutoUpdating(mapAutoUpdating); }
	
	public boolean isUsingAdminMode() { return MPlayer.get(this).isUsingAdminMode(); }
	public void setUsingAdminMode(boolean usingAdminMode) { MPlayer.get(this).setUsingAdminMode(usingAdminMode); }
	
	// -------------------------------------------- //
	// CORE UTILITIES
	// -------------------------------------------- //
	
	public void resetFactionData()
	{
		// The default neutral faction
		this.setFactionId(null); 
		this.setRole(null);
		this.setTitle(null);
		this.setAutoClaimFaction(null);
	}
	
	/*
	public boolean isPresent(boolean requireFetchable)
	{
		if (!this.isOnline()) return false;
		
		if (requireFetchable)
		{
			
		}
		else
		{
			
		}
		
		PS ps = Mixin.getSenderPs(this.getId());
		if (ps == null) return false;
		
		String psUniverse = Factions.get().getMultiverse().getUniverseForWorldName(ps.getWorld());
		if (!psUniverse.equals(this.getUniverse())) return false;
		
		if (!requireFetchable) return true;
		
		Player player = this.getPlayer();
		if (player == null) return false;
		
		if (player.isDead()) return false;
		
		return true;
	}
	*/
	
	// -------------------------------------------- //
	// FIELD: factionId
	// -------------------------------------------- //
	
	public String getDefaultFactionId()
	{
		return UConf.get(this).defaultPlayerFactionId;
	}
	
	// This method never returns null
	public String getFactionId()
	{
		if (this.factionId == null) return this.getDefaultFactionId();
		return this.factionId;
	}
	
	// This method never returns null
	public Faction getFaction()
	{
		Faction ret = FactionColls.get().get(this).get(this.getFactionId());
		if (ret == null) ret = FactionColls.get().get(this).get(UConf.get(this).defaultPlayerFactionId);
		return ret;
	}
	
	public boolean hasFaction()
	{
		return !this.getFactionId().equals(UConf.get(this).factionIdNone);
	}
	
	// This setter is so long because it search for default/null case and takes care of updating the faction member index 
	public void setFactionId(String factionId)
	{
		// Avoid null input
		if (factionId == null) factionId = this.getDefaultFactionId();
		
		// Get the old value
		String oldFactionId = this.getFactionId();
		
		// Ignore nochange
		if (factionId.equals(oldFactionId)) return;
		
		// Apply change
		if (factionId.equals(this.getDefaultFactionId())) factionId = null;
		this.factionId = factionId;
		
		// Next we must be attached and inited
		if (!this.attached()) return;
		if (!this.getColl().inited()) return;
		if (!Factions.get().isDatabaseInitialized()) return;
		
		// Update index
		Faction oldFaction = FactionColls.get().get(this).get(oldFactionId);
		Faction faction = FactionColls.get().get(this).get(factionId);
		
		oldFaction.uplayers.remove(this);
		faction.uplayers.add(this);
		
		// Mark as changed
		this.changed();
	}
	
	public void setFaction(Faction faction)
	{
		this.setFactionId(faction.getId());
	}
	
	// -------------------------------------------- //
	// FIELD: role
	// -------------------------------------------- //
	
	public Rel getDefaultRole()
	{
		return UConf.get(this).defaultPlayerRole;
	}
	
	public Rel getRole()
	{
		if (this.role == null) return this.getDefaultRole();
		return this.role;
	}
	
	public void setRole(Rel role)
	{
		if (role == null || MUtil.equals(role, this.getDefaultRole())) role = null;
		this.role = role;
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: title
	// -------------------------------------------- //
	
	public boolean hasTitle()
	{
		return this.title != null;
	}
	
	public String getTitle()
	{
		if (this.hasTitle()) return this.title;
		return Lang.PLAYER_NOTITLE;
	}
	
	public void setTitle(String title)
	{
		if (title != null)
		{
			title = title.trim();
			if (title.length() == 0)
			{
				title = null;
			}
		}
		this.title = title;
		this.changed();
	}
	
	// -------------------------------------------- //
	// FIELD: power
	// -------------------------------------------- //
	
	// MIXIN: RAW
	
	public double getPowerMaxUniversal()
	{
		return Factions.get().getPowerMixin().getMaxUniversal(this);
	}
	
	public double getPowerMax()
	{
		return Factions.get().getPowerMixin().getMax(this);
	}
	
	public double getPowerMin()
	{
		return Factions.get().getPowerMixin().getMin(this);
	}
	
	public double getPowerPerHour()
	{
		return Factions.get().getPowerMixin().getPerHour(this);
	}
	
	public double getPowerPerDeath()
	{
		return Factions.get().getPowerMixin().getPerDeath(this);
	}
	
	// MIXIN: FINER
	
	public double getLimitedPower(double power)
	{
		power = Math.max(power, this.getPowerMin());
		power = Math.min(power, this.getPowerMax());
		return power;
	}
	
	// RAW
	
	public double getDefaultPower()
	{
		return UConf.get(this).defaultPlayerPower;
	}
	
	public double getPower()
	{
		Double ret = this.power;
		if (ret == null) ret = this.getDefaultPower();
		ret = this.getLimitedPower(ret);
		return ret;
	}
	
	public void setPower(Double power)
	{
		if (power == null || MUtil.equals(power, this.getDefaultPower())) power = null;
		power = this.getLimitedPower(power);
		this.power = power;
		this.changed();
	}
	
	// FINER
	
	public int getPowerRounded()
	{
		return (int) Math.round(this.getPower());
	}
	
	// -------------------------------------------- //
	// TITLE, NAME, FACTION TAG AND CHAT
	// -------------------------------------------- //
	
	public String getName()
	{
		return this.getFixedId();
	}
	
	public String getTag()
	{
		Faction faction = this.getFaction();
		if (faction.isNone()) return "";
		return faction.getTag();
	}
	
	// Base concatenations:
	
	public String getNameAndSomething(String something)
	{
		String ret = this.role.getPrefix();
		if (something.length() > 0)
		{
			ret += something+" ";
		}
		ret += this.getName();
		return ret;
	}
	
	public String getNameAndTitle()
	{
		if (this.hasTitle())
		{
			return this.getNameAndSomething(this.getTitle());
		}
		else
		{
			return this.getName();
		}
	}
	
	public String getNameAndTag()
	{
		return this.getNameAndSomething(this.getTag());
	}
	
	// Colored concatenations:
	// These are used in information messages
	
	public String getNameAndTitle(Faction faction)
	{
		return this.getColorTo(faction)+this.getNameAndTitle();
	}
	public String getNameAndTitle(UPlayer uplayer)
	{
		return this.getColorTo(uplayer)+this.getNameAndTitle();
	}
	
	// -------------------------------------------- //
	// RELATION AND RELATION COLORS
	// -------------------------------------------- //
	
	@Override
	public String describeTo(RelationParticipator observer, boolean ucfirst)
	{
		return RelationUtil.describeThatToMe(this, observer, ucfirst);
	}
	
	@Override
	public String describeTo(RelationParticipator observer)
	{
		return RelationUtil.describeThatToMe(this, observer);
	}
	
	@Override
	public Rel getRelationTo(RelationParticipator observer)
	{
		return RelationUtil.getRelationOfThatToMe(this, observer);
	}
	
	@Override
	public Rel getRelationTo(RelationParticipator observer, boolean ignorePeaceful)
	{
		return RelationUtil.getRelationOfThatToMe(this, observer, ignorePeaceful);
	}
	
	public Rel getRelationToLocation()
	{
		// TODO: Use some built in system to get sender
		return BoardColls.get().getFactionAt(PS.valueOf(this.getPlayer())).getRelationTo(this);
	}
	
	@Override
	public ChatColor getColorTo(RelationParticipator observer)
	{
		return RelationUtil.getColorOfThatToMe(this, observer);
	}
	
	// -------------------------------------------- //
	// HEALTH
	// -------------------------------------------- //
	
	public void heal(int amnt)
	{
		Player player = this.getPlayer();
		if (player == null)
		{
			return;
		}
		player.setHealth(player.getHealth() + amnt);
	}
	
	// -------------------------------------------- //
	// TERRITORY
	// -------------------------------------------- //
	
	public boolean isInOwnTerritory()
	{
		// TODO: Use Mixin to get this PS instead
		return BoardColls.get().getFactionAt(Mixin.getSenderPs(this.getId())) == this.getFaction();
	}

	public boolean isInEnemyTerritory()
	{
		// TODO: Use Mixin to get this PS instead
		return BoardColls.get().getFactionAt(Mixin.getSenderPs(this.getId())).getRelationTo(this) == Rel.ENEMY;
	}
	
	// -------------------------------------------- //
	// ACTIONS
	// -------------------------------------------- //
	
	public void leave()
	{
		Faction myFaction = this.getFaction();

		boolean permanent = myFaction.getFlag(FFlag.PERMANENT);
		
		if (myFaction.getUPlayers().size() > 1)
		{
			if (!permanent && this.getRole() == Rel.LEADER)
			{
				msg("<b>You must give the leader role to someone else first.");
				return;
			}
			
			if (!UConf.get(myFaction).canLeaveWithNegativePower && this.getPower() < 0)
			{
				msg("<b>You cannot leave until your power is positive.");
				return;
			}
		}

		// Event
		FactionsEventMembershipChange membershipChangeEvent = new FactionsEventMembershipChange(sender, this, myFaction, MembershipChangeReason.LEAVE);
		membershipChangeEvent.run();
		if (membershipChangeEvent.isCancelled()) return;
		
		if (myFaction.isNormal())
		{
			for (UPlayer uplayer : myFaction.getUPlayersWhereOnline(true))
			{
				uplayer.msg("%s<i> left %s<i>.", this.describeTo(uplayer, true), myFaction.describeTo(uplayer));
			}

			if (MConf.get().logFactionLeave)
			{
				Factions.get().log(this.getName()+" left the faction: "+myFaction.getTag());
			}
		}
		
		this.resetFactionData();

		if (myFaction.isNormal() && !permanent && myFaction.getUPlayers().isEmpty())
		{
			// Remove this faction
			for (UPlayer uplayer : UPlayerColls.get().get(this).getAllOnline())
			{
				uplayer.msg("<i>%s<i> was disbanded.", myFaction.describeTo(uplayer, true));
			}

			myFaction.detach();
			if (MConf.get().logFactionDisband)
			{
				Factions.get().log("The faction "+myFaction.getTag()+" ("+myFaction.getId()+") was disbanded due to the last player ("+this.getName()+") leaving.");
			}
		}
	}
	
	public boolean tryClaim(Faction newFaction, PS ps, boolean verbooseChange, boolean verbooseSame)
	{
		PS chunk = ps.getChunk(true);
		Faction oldFaction = BoardColls.get().getFactionAt(chunk);
		
		UConf uconf = UConf.get(newFaction);
		MConf mconf = MConf.get();
		
		// Validate
		if (newFaction == oldFaction)
		{
			msg("%s<i> already owns this land.", newFaction.describeTo(this, true));
			return true;
		}
		
		if (!this.isUsingAdminMode() && newFaction.isNormal())
		{
			int ownedLand = newFaction.getLandCount();
			
			if (!uconf.claimingFromOthersAllowed && oldFaction.isNormal())
			{
				msg("<b>You may not claim land from others.");
				return false;
			}
			
			if (mconf.worldsNoClaiming.contains(ps.getWorld()))
			{
				msg("<b>Sorry, this world has land claiming disabled.");
				return false;
			}
			
			if (oldFaction.getRelationTo(newFaction).isAtLeast(Rel.TRUCE))
			{
				msg("<b>You can't claim this land due to your relation with the current owner.");
				return false;
			}
			
			if (newFaction.getUPlayers().size() < uconf.claimsRequireMinFactionMembers)
			{
				msg("Factions must have at least <h>%s<b> members to claim land.", uconf.claimsRequireMinFactionMembers);
				return false;
			}
			
			if (uconf.claimedLandsMax != 0 && ownedLand >= uconf.claimedLandsMax && ! newFaction.getFlag(FFlag.INFPOWER))
			{
				msg("<b>Limit reached. You can't claim more land.");
				return false;
			}
			
			if (ownedLand >= newFaction.getPowerRounded())
			{
				msg("<b>You can't claim more land. You need more power.");
				return false;
			}
			
			if
			(
				uconf.claimsMustBeConnected
				&& newFaction.getLandCountInWorld(ps.getWorld()) > 0
				&& !BoardColls.get().isConnectedPs(ps, newFaction)
				&& (!uconf.claimsCanBeUnconnectedIfOwnedByOtherFaction || !oldFaction.isNormal())
			)
			{
				if (uconf.claimsCanBeUnconnectedIfOwnedByOtherFaction)
				{
					msg("<b>You can only claim additional land which is connected to your first claim or controlled by another faction!");
				}
				else
				{
					msg("<b>You can only claim additional land which is connected to your first claim!");
				}
				return false;
			}
			
			if (!oldFaction.hasLandInflation())
			{
				msg("%s<i> owns this land and is strong enough to keep it.", oldFaction.getTag(this));
				return false;
			}
			
			if ( ! BoardColls.get().isBorderPs(ps))
			{
				msg("<b>You must start claiming land at the border of the territory.");
				return false;
			}
		}
		
		// Event
		FactionsEventChunkChange event = new FactionsEventChunkChange(sender, chunk, newFaction);
		event.run();
		if (event.isCancelled()) return false;

		// Apply
		BoardColls.get().setFactionAt(chunk, newFaction);
		
		// Inform
		Set<UPlayer> informees = new HashSet<UPlayer>();
		informees.add(this);
		if (newFaction.isNormal())
		{
			informees.addAll(newFaction.getUPlayers());
		}
		if (oldFaction.isNormal())
		{
			informees.addAll(oldFaction.getUPlayers());
		}
		if (MConf.get().logLandClaims)
		{
			informees.add(UPlayer.get(SenderUtil.getConsole()));
		}
		
		for (UPlayer informee : informees)
		{
			informee.msg("<h>%s<i> did %s %s <i>for <h>%s<i> from <h>%s<i>.", this.describeTo(informee, true), event.getType().toString().toLowerCase(), chunk.toString(PSFormatSlug.get()), newFaction.describeTo(informee), oldFaction.describeTo(informee));
		}

		return true;
	}
	
}
