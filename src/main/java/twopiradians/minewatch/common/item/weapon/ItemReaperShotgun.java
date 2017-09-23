package twopiradians.minewatch.common.item.weapon;

import java.util.HashMap;

import javax.annotation.Nullable;

import com.google.common.collect.Maps;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import twopiradians.minewatch.common.Minewatch;
import twopiradians.minewatch.common.entity.EntityMWThrowable;
import twopiradians.minewatch.common.entity.EntityReaperBullet;
import twopiradians.minewatch.common.hero.Ability;
import twopiradians.minewatch.common.hero.EnumHero;
import twopiradians.minewatch.common.item.armor.ItemMWArmor;
import twopiradians.minewatch.common.sound.ModSoundEvents;
import twopiradians.minewatch.common.tickhandler.Handlers;
import twopiradians.minewatch.common.tickhandler.TickHandler;
import twopiradians.minewatch.common.tickhandler.TickHandler.Handler;
import twopiradians.minewatch.common.tickhandler.TickHandler.Identifier;
import twopiradians.minewatch.packet.SPacketSimple;
import twopiradians.minewatch.packet.SPacketSpawnParticle;

public class ItemReaperShotgun extends ItemMWWeapon {

	public static HashMap<EntityPlayer, Boolean> wraithViewBobbing = Maps.newHashMap();
	public static final Handler WRAITH = new Handler(Identifier.REAPER_WRAITH, false) {
		@Override
		@SideOnly(Side.CLIENT)
		public boolean onClientTick() {
			if (player == Minecraft.getMinecraft().player)
				if (this.ticksLeft > 1)
					Minecraft.getMinecraft().gameSettings.viewBobbing = false;
				else if (wraithViewBobbing.containsKey(player)) {
					Minecraft.getMinecraft().gameSettings.viewBobbing = wraithViewBobbing.get(player);
					wraithViewBobbing.remove(player);
				}
			if (this.ticksLeft > 8) {
				boolean firstPerson = player == Minecraft.getMinecraft().player && 
						Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 ;
				for (int i=0; i<3; ++i) {
					player.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, 
							player.posX+player.world.rand.nextDouble()-0.5d, 
							player.posY+player.world.rand.nextDouble()*(firstPerson ? 0d : 1.5d), 
							player.posZ+player.world.rand.nextDouble()-0.5d, 
							0, (firstPerson ? -0.1d : 0d), 0);
					player.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, 
							player.posX+player.world.rand.nextDouble()-0.5d, 
							player.posY+player.world.rand.nextDouble()*(firstPerson ? 0d : 1.5d), 
							player.posZ+player.world.rand.nextDouble()-0.5d, 
							0, (firstPerson ? -0.1d : 0d), 0);
				}
			}
			return super.onClientTick();
		}

		@Override
		public boolean onServerTick() {
			// set resistance high to prevent hurt sound/animations 
			// (player.hurtResistantTime > player.maxHurtResistantTime / 2.0F)
			player.hurtResistantTime = (int) (player.maxHurtResistantTime*2.1f); 
			return super.onServerTick();
		}

		@Override
		public Handler onRemove() {
			if (!player.world.isRemote) {
				EnumHero.REAPER.ability2.keybind.setCooldown(player, 160, false);
				player.hurtResistantTime = 0;
			}
			return super.onRemove();
		}
	};

	public static HashMap<EntityPlayer, Integer> tpThirdPersonView = Maps.newHashMap();
	public static final Handler TPS = new Handler(Identifier.REAPER_TELEPORT, true) {
		@Override
		@SideOnly(Side.CLIENT)
		public boolean onClientTick() {
			// stop handler and play sound if needed
			if ((player.getHeldItemMainhand() == null || player.getHeldItemMainhand().getItem() != EnumHero.REAPER.weapon ||
					!EnumHero.REAPER.ability1.isSelected(player) || 
					!EnumHero.REAPER.weapon.canUse(player, true, EnumHand.MAIN_HAND)) && this.ticksLeft == -1) {
				player.playSound(ModSoundEvents.reaperTeleportStop, 1.0f, 1.0f);
				return true;
			}
			else {		
				// change view
				if (this.ticksLeft != -1 && Minecraft.getMinecraft().player == player) {
					if (this.ticksLeft > 1)
						Minecraft.getMinecraft().gameSettings.thirdPersonView = 1;
					else if (tpThirdPersonView.containsKey(player)) {
						Minecraft.getMinecraft().gameSettings.thirdPersonView = tpThirdPersonView.get(player);
						tpThirdPersonView.remove(player);
					}
				}
				// particles
				if (player.ticksExisted % 2 == 0)
					Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, false, 1);
				else if (player.ticksExisted % 3 == 0)
					Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, false, 3);
				Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, false, 2);
				// tp sound
				if (player.ticksExisted % 13 == 0 && this.ticksLeft == -1)
					player.playSound(ModSoundEvents.reaperTeleportDuring, player.world.rand.nextFloat()*0.5f+0.3f, player.world.rand.nextFloat()*0.5f+0.75f);
				// particles at player
				if (this.ticksLeft > 40 && this.ticksLeft != -1) {
					if (player.ticksExisted % 2 == 0)
						Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, true, 1);
					else if (player.ticksExisted % 3 == 0)
						Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, true, 3);
					Minewatch.proxy.spawnParticlesReaperTeleport(player.world, player, true, 2);
				}
			}
			return this.ticksLeft != -1 && --this.ticksLeft <= 0;
		}

		@Override
		public boolean onServerTick() {
			if (this.ticksLeft == 40) {
				if (player.isRiding())
					player.dismountRidingEntity();
				player.setPositionAndUpdate(this.position.xCoord, 
						this.position.yCoord, 
						this.position.zCoord);
				if (player.world.rand.nextBoolean())
					player.world.playSound(null, player.getPosition(), ModSoundEvents.reaperTeleportVoice, SoundCategory.PLAYERS, 1.0f, 1.0f);
			}
			return super.onServerTick();
		}

		@Override
		public Handler onRemove() {
			if (player.world.isRemote && this.ticksLeft != -1) 
				EnumHero.REAPER.ability1.toggle(player, false);
			else if (!player.world.isRemote)
				EnumHero.REAPER.ability1.keybind.setCooldown(player, 200, false); 
			return super.onRemove();
		}
	};

	public ItemReaperShotgun() {
		super(30);
		this.hasOffhand = true;
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	public void onItemLeftClick(ItemStack stack, World world, EntityPlayer player, EnumHand hand) { 
		// shoot
		if (this.canUse(player, true, hand) && !world.isRemote && 
				TickHandler.getHandler(player, Identifier.REAPER_TELEPORT) == null && 
				!hero.ability1.isSelected(player)) {			
			for (int i=0; i<20; i++) {
				EntityReaperBullet bullet = new EntityReaperBullet(world, player, hand);
				bullet.setAim(player, player.rotationPitch, player.rotationYaw, 3.0F, 4F, 1F, hand, false);
				world.spawnEntity(bullet);
			}
			world.playSound(null, player.posX, player.posY, player.posZ, 
					ModSoundEvents.reaperShoot, SoundCategory.PLAYERS, 
					world.rand.nextFloat()+0.5F, world.rand.nextFloat()/2+0.75f);	
			Vec3d vec = EntityMWThrowable.getShootingPos(player, player.rotationPitch, player.rotationYaw, hand);
			Minewatch.network.sendToAllAround(new SPacketSpawnParticle(0, vec.xCoord, vec.yCoord, vec.zCoord, 0xD93B1A, 0x510D30, 5, 5), 
					new TargetPoint(world.provider.getDimension(), player.posX, player.posY, player.posZ, 128));

			this.subtractFromCurrentAmmo(player, 1, hand);
			if (!player.getCooldownTracker().hasCooldown(this))
				player.getCooldownTracker().setCooldown(this, 11);
			if (world.rand.nextInt(8) == 0)
				player.getHeldItem(hand).damageItem(1, player);
		}
	}

	@SuppressWarnings("deprecation")
	@Nullable
	private Vec3d getTeleportPos(EntityPlayer player) {
		try {
			RayTraceResult result = player.world.rayTraceBlocks(player.getPositionEyes(1), 
					player.getLookVec().scale(Integer.MAX_VALUE), true, true, true);
			if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK && result.hitVec != null) {
				BlockPos pos = new BlockPos(result.hitVec.xCoord, result.getBlockPos().getY(), result.hitVec.zCoord);

				double adjustZ = result.sideHit == EnumFacing.SOUTH ? -0.5d : 0;
				double adjustX = result.sideHit == EnumFacing.EAST ? -0.5d : 0;

				pos = pos.add(adjustX, 0, adjustZ);
				IBlockState state = player.world.getBlockState(pos);
				IBlockState state1 = player.world.getBlockState(pos.up());
				IBlockState state2 = player.world.getBlockState(pos.up(2));

				if ((player.world.isAirBlock(pos.up()) || state1.getBlock().getCollisionBoundingBox(state1, player.world, pos.up()) == null ||
						state1.getBlock().getCollisionBoundingBox(state1, player.world, pos.up()) == Block.NULL_AABB) && 
						(player.world.isAirBlock(pos.up(2)) || state2.getBlock().getCollisionBoundingBox(state2, player.world, pos.up(2)) == null ||
						state2.getBlock().getCollisionBoundingBox(state2, player.world, pos.up(2)) == Block.NULL_AABB) && 
						!player.world.isAirBlock(pos) && 
						state.getBlock().getCollisionBoundingBox(state, player.world, pos) != null &&
						state.getBlock().getCollisionBoundingBox(state, player.world, pos) != Block.NULL_AABB &&
						Math.sqrt(result.getBlockPos().distanceSq(player.posX, player.posY, player.posZ)) <= 35)
					return new Vec3d(result.hitVec.xCoord + adjustX, 
							result.getBlockPos().getY()+1+(state.getBlock() instanceof BlockFence ? 0.5d : 0), 
							result.hitVec.zCoord + adjustZ);
			}
		}
		catch (Exception e) {}
		return null;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
		super.onUpdate(stack, world, entity, itemSlot, isSelected);

		if (entity instanceof EntityPlayer && isSelected) {
			EntityPlayer player = (EntityPlayer)entity;
			// teleport
			if (hero.ability1.isSelected(player) && this.canUse((EntityPlayer) entity, true, EnumHand.MAIN_HAND)) {   
				if (world.isRemote) {
					Vec3d tpVec = this.getTeleportPos(player);
					Handler handler = TickHandler.getHandler(player, Identifier.REAPER_TELEPORT);
					if (tpVec != null) {
						if (handler == null) {
							TickHandler.register(true, TPS.setEntity(player).setPosition(tpVec).setTicks(-1));
							Minewatch.proxy.spawnParticlesReaperTeleport(world, player, false, 0);
							if (Minewatch.keys.ability2(player))
								player.playSound(ModSoundEvents.reaperTeleportStart, 1.0f, 1.0f);
						}
						else if (handler.ticksLeft == -1)
							handler.setPosition(tpVec);
					}
					else if (handler != null && handler.ticksLeft == -1)
						TickHandler.unregister(true, handler);
				}
				else if (Minewatch.keys.lmb(player)) {
					Vec3d tpVec = this.getTeleportPos(player);
					if (tpVec != null && player instanceof EntityPlayerMP) {
						Minewatch.network.sendToAll(new SPacketSimple(1, player, Math.floor(tpVec.xCoord)+0.5d, tpVec.yCoord, Math.floor(tpVec.zCoord)+0.5d));
						Minewatch.proxy.playFollowingSound(player, ModSoundEvents.reaperTeleportFinal, SoundCategory.PLAYERS, 1.0f, 1.0f);
						TickHandler.register(false, TPS.setEntity(player).setTicks(70).setPosition(new Vec3d(Math.floor(tpVec.xCoord)+0.5d, tpVec.yCoord, Math.floor(tpVec.zCoord)+0.5d)),
								Ability.ABILITY_USING.setEntity(player).setTicks(70),
								Handlers.PREVENT_INPUT.setEntity(player).setTicks(70),
								Handlers.PREVENT_MOVEMENT.setEntity(player).setTicks(70), 
								Handlers.PREVENT_ROTATION.setEntity(player).setTicks(70));
						Minewatch.network.sendTo(new SPacketSimple(9, player, false, 70, 0, 0), (EntityPlayerMP) player);
					}
				}

				if (Minewatch.keys.rmb(player))
					hero.ability1.toggle(player, false);
			}
			// wraith
			else if (hero.ability2.isSelected(player) && !world.isRemote && player instanceof EntityPlayerMP &&
					 this.canUse((EntityPlayer) entity, true, EnumHand.MAIN_HAND)) {
				TickHandler.register(false, Ability.ABILITY_USING.setEntity(player).setTicks(60),
						WRAITH.setEntity(player).setTicks(60));
				Minewatch.network.sendTo(new SPacketSimple(10, false, player), (EntityPlayerMP) player);
				this.setCurrentAmmo(player, this.getMaxAmmo(player), EnumHand.MAIN_HAND, EnumHand.OFF_HAND);
				player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 60, 1, true, false));
				Minewatch.proxy.playFollowingSound(player, ModSoundEvents.reaperWraith, SoundCategory.PLAYERS, 1, 1);
			}
		}
	}

	@SubscribeEvent
	public void passiveHeal(LivingHurtEvent event) {
		if (event.getSource().getSourceOfDamage() instanceof EntityPlayer) {
			EntityPlayer player = ((EntityPlayer)event.getSource().getSourceOfDamage());
			if (!player.world.isRemote && ItemMWArmor.SetManager.playersWearingSets.get(player.getPersistentID()) == hero &&
					player.getHeldItemMainhand() != null && player.getHeldItemMainhand().getItem() == this) {
				try {
					float damage = event.getAmount();
					damage = CombatRules.getDamageAfterAbsorb(damage, (float)player.getTotalArmorValue(), (float)player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
					damage = this.applyPotionDamageCalculations(player, event.getSource(), damage);
					if (damage >= 0) 
						player.heal(damage * 0.2f);
				}
				catch (Exception e) {}
			}
		}
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return (Minewatch.proxy.getClientPlayer() != null && 
				TickHandler.hasHandler(Minewatch.proxy.getClientPlayer(), Identifier.REAPER_WRAITH)) ? 
						true : super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void renderWraithOverlay(RenderGameOverlayEvent.Pre event) {
		EntityPlayer player = Minecraft.getMinecraft().player;
		if (event.getType() == ElementType.ALL && player != null && 
				TickHandler.hasHandler(player, Identifier.REAPER_WRAITH)) {
			float ticks = TickHandler.hasHandler(player, Identifier.REAPER_WRAITH) ? 
					60 - TickHandler.getHandler(player, Identifier.REAPER_WRAITH).ticksLeft+Minecraft.getMinecraft().getRenderPartialTicks() : 10;
					double height = event.getResolution().getScaledHeight_double();
					double width = event.getResolution().getScaledWidth_double();

					GlStateManager.pushMatrix();
					GlStateManager.enableBlend();
					GlStateManager.scale(width/256d, height/256d, 1);
					int firstImage = (int) (ticks / 10);
					int secondImage = firstImage + 1;
					if (firstImage < 6) {
						GlStateManager.color(1, 1, 1, 1.1f-((ticks) % 10)/10f);
						Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(Minewatch.MODID, "textures/gui/reaper_wraith_"+firstImage+".png"));
						GuiUtils.drawTexturedModalRect(0, 0, 0, 0, 256, 256, 0);
					}
					if (secondImage < 6) {
						GlStateManager.color(1, 1, 1, (ticks % 10)/10f);
						Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(Minewatch.MODID, "textures/gui/reaper_wraith_"+secondImage+".png"));
						GuiUtils.drawTexturedModalRect(0, 0, 0, 0, 256, 256, 0);
					}
					GlStateManager.popMatrix();
		}
	}

	@SubscribeEvent
	public void preventWraithDamage(LivingHurtEvent event) {
		if (TickHandler.hasHandler(event.getEntity(), Identifier.REAPER_WRAITH) &&
				!event.getSource().canHarmInCreative()) 
			event.setCanceled(true);
	}

	@SubscribeEvent
	@SideOnly(Side.CLIENT)
	public void moveTpCamera(FOVUpdateEvent event) {
		if (Minecraft.getMinecraft().world != null &&
				TickHandler.getHandler(Minecraft.getMinecraft().player, Identifier.REAPER_TELEPORT) != null &&
				TickHandler.getHandler(Minecraft.getMinecraft().player, Identifier.REAPER_TELEPORT).ticksLeft > 0) 
			event.setNewfov(event.getFov()+0.8f);
	}

	/**Copied from EntityLivingBase bc it's protected*/
	private float applyPotionDamageCalculations(EntityPlayer player, DamageSource source, float damage) {
		if (source.isDamageAbsolute())
			return damage;
		else {
			if (player.isPotionActive(MobEffects.RESISTANCE) && source != DamageSource.OUT_OF_WORLD) {
				int i = (player.getActivePotionEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
				int j = 25 - i;
				float f = damage * (float)j;
				damage = f / 25.0F;
			}
			if (damage <= 0.0F)
				return 0.0F;
			else {
				int k = EnchantmentHelper.getEnchantmentModifierDamage(player.getArmorInventoryList(), source);
				if (k > 0)
					damage = CombatRules.getDamageAfterMagicAbsorb(damage, (float)k);
				return damage;
			}
		}
	}

}
