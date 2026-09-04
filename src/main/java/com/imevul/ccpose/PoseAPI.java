package com.imevul.ccpose;

import dan200.computercraft.api.component.ComputerComponents;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.turtle.ITurtleAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PoseAPI implements ILuaAPI {
	private static final ConcurrentHashMap<Integer, PoseAPI> RUNNING = new ConcurrentHashMap<>();

	private final IComputerSystem computer;

	public PoseAPI(IComputerSystem computer) {
		this.computer = computer;
	}

	@Override
	public String[] getNames() {
		return new String[] { "pose" };
	}

	@Override
	public void startup() {
		try {
			RUNNING.put(computer.getID(), this);
		} catch (RuntimeException ignored) {
		}
	}

	@Override
	public void shutdown() {
		try {
			RUNNING.remove(computer.getID(), this);
		} catch (RuntimeException ignored) {
		}
	}

	@LuaFunction(mainThread = true)
	public final @Nullable Map<String, Object> get(IArguments args) throws LuaException {
		String name = requireName(args);
		try {
			return toTable(findPlayer(name));
		} catch (RuntimeException e) {
			return null;
		}
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> list() {
		List<Map<String, Object>> players = new ArrayList<>();
		try {
			MinecraftServer server = server();
			if (server == null) {
				return players;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					Map<String, Object> table = playerTable(player);
					if (table != null) {
						players.add(table);
					}
				} catch (RuntimeException ignored) {
				}
			}
		} catch (RuntimeException ignored) {
		}
		return players;
	}

	@LuaFunction(mainThread = true)
	public final @Nullable Map<String, Object> self() {
		try {
			IPocketAccess pocket = computer.getComponent(ComputerComponents.POCKET);
			if (pocket != null) {
				Entity entity = pocket.getEntity();
				if (entity instanceof ServerPlayer player) {
					return playerTable(player);
				}
			}
			return wearerFallback();
		} catch (RuntimeException e) {
			return null;
		}
	}

	@LuaFunction(mainThread = true)
	public final boolean online(IArguments args) throws LuaException {
		String name = requireName(args);
		try {
			return findPlayer(name) != null;
		} catch (RuntimeException e) {
			return false;
		}
	}

	@LuaFunction(mainThread = true)
	public final @Nullable Map<String, Object> here() {
		return machineTable(computer);
	}

	@LuaFunction(mainThread = true)
	public final @Nullable Map<String, Object> computer(IArguments args) throws LuaException {
		String type = args.getType(0);
		if ("number".equals(type)) {
			int id = args.getInt(0);
			try {
				PoseAPI api = RUNNING.get(id);
				return api == null ? null : machineTable(api.computer);
			} catch (RuntimeException e) {
				return null;
			}
		}
		if ("string".equals(type)) {
			String label = args.getString(0);
			if (label.isEmpty()) {
				throw new LuaException("expected a non-empty string");
			}
			return findMachineByLabel(label);
		}
		throw new LuaException("expected a number or string");
	}

	@LuaFunction(mainThread = true)
	public final List<Map<String, Object>> computers() {
		List<Map<String, Object>> machines = new ArrayList<>();
		try {
			for (PoseAPI api : RUNNING.values()) {
				try {
					Map<String, Object> table = machineTable(api.computer);
					if (table != null) {
						machines.add(table);
					}
				} catch (RuntimeException ignored) {
				}
			}
		} catch (RuntimeException ignored) {
		}
		return machines;
	}

	// Smart Glasses are not a pocket computer, so POCKET is null. Use the
	// only player in this level, or the player whose items match our id.
	private @Nullable Map<String, Object> wearerFallback() {
		try {
			MinecraftServer server = server();
			if (server == null) {
				return null;
			}
			ServerLevel level = computer.getLevel();
			List<ServerPlayer> inLevel = new ArrayList<>();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					if (level == null || player.level() == level) {
						inLevel.add(player);
					}
				} catch (RuntimeException ignored) {
				}
			}
			if (inLevel.size() == 1) {
				return playerTable(inLevel.get(0));
			}
			int id = computer.getID();
			for (ServerPlayer player : inLevel) {
				try {
					if (holdsComputer(player, id)) {
						return playerTable(player);
					}
				} catch (RuntimeException ignored) {
				}
			}
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String requireName(IArguments args) throws LuaException {
		String name = args.getString(0);
		if (name.isEmpty()) {
			throw new LuaException("expected a non-empty string");
		}
		return name;
	}

	private @Nullable ServerPlayer findPlayer(String name) {
		MinecraftServer server = server();
		if (server == null) {
			return null;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
					return player;
				}
			} catch (RuntimeException ignored) {
			}
		}
		return null;
	}

	private static @Nullable Map<String, Object> findMachineByLabel(String label) {
		try {
			for (PoseAPI api : RUNNING.values()) {
				try {
					String other = api.computer.getLabel();
					if (other != null && other.equalsIgnoreCase(label)) {
						return machineTable(api.computer);
					}
				} catch (RuntimeException ignored) {
				}
			}
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	private @Nullable MinecraftServer server() {
		try {
			ServerLevel level = computer.getLevel();
			return level == null ? null : level.getServer();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable Map<String, Object> toTable(@Nullable ServerPlayer player) {
		return player == null ? null : playerTable(player);
	}

	private static boolean holdsComputer(ServerPlayer player, int id) {
		try {
			if (stackHasComputerId(player.getMainHandItem(), id)
				|| stackHasComputerId(player.getOffhandItem(), id)) {
				return true;
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				try {
					if (stackHasComputerId(player.getInventory().getItem(i), id)) {
						return true;
					}
				} catch (RuntimeException ignored) {
				}
			}
			return false;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean stackHasComputerId(ItemStack stack, int id) {
		try {
			if (stack == null || stack.isEmpty()) {
				return false;
			}
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			if (data == null) {
				return false;
			}
			var nbt = data.copyTag();
			if (nbt.contains("ComputerId") && nbt.getInt("ComputerId") == id) {
				return true;
			}
			if (nbt.contains("computer_id") && nbt.getInt("computer_id") == id) {
				return true;
			}
			return nbt.contains("Id") && nbt.getInt("Id") == id;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static @Nullable Map<String, Object> playerTable(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		try {
			Map<String, Object> table = new HashMap<>();
			table.put("name", player.getGameProfile().getName());
			table.put("x", player.getX());
			table.put("y", player.getY());
			table.put("z", player.getZ());
			table.put("yaw", (double) player.getYRot());
			table.put("pitch", (double) player.getXRot());
			table.put("dimension", player.level().dimension().location().toString());
			return table;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable Map<String, Object> machineTable(IComputerSystem system) {
		try {
			if (system == null) {
				return null;
			}
			ITurtleAccess turtle = system.getComponent(ComputerComponents.TURTLE);
			IPocketAccess pocket = system.getComponent(ComputerComponents.POCKET);

			String type;
			if (turtle != null) {
				type = "turtle";
			} else if (pocket != null) {
				type = "pocket";
			} else {
				type = "computer";
			}

			Double x = null;
			Double y = null;
			Double z = null;
			if (pocket != null) {
				Vec3 pos = pocket.getPosition();
				if (pos != null) {
					x = pos.x;
					y = pos.y;
					z = pos.z;
				}
			}
			if (x == null) {
				BlockPos pos = system.getPosition();
				if (pos != null) {
					x = (double) pos.getX();
					y = (double) pos.getY();
					z = (double) pos.getZ();
				}
			}
			if (x == null) {
				return null;
			}

			Map<String, Object> table = new HashMap<>();
			table.put("id", system.getID());
			String label = system.getLabel();
			if (label != null && !label.isEmpty()) {
				table.put("label", label);
			}
			table.put("type", type);
			table.put("x", x);
			table.put("y", y);
			table.put("z", z);

			Direction facing = facingOf(system, turtle, pocket);
			if (facing != null) {
				table.put("facing", facing.getSerializedName());
				table.put("yaw", (double) facing.toYRot());
			}

			ServerLevel level = system.getLevel();
			if (level != null) {
				table.put("dimension", level.dimension().location().toString());
			}
			return table;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static @Nullable Direction facingOf(
		IComputerSystem system,
		@Nullable ITurtleAccess turtle,
		@Nullable IPocketAccess pocket
	) {
		try {
			if (turtle != null && !turtle.isRemoved()) {
				return turtle.getDirection();
			}
			// Pockets are items; the block at last-known pos is not the computer.
			if (pocket != null) {
				return null;
			}
			ServerLevel level = system.getLevel();
			BlockPos pos = system.getPosition();
			if (level == null || pos == null) {
				return null;
			}
			BlockState state = level.getBlockState(pos);
			if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
			}
			return null;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
