package dev.worldconfigmanager.fabric.net;

import dev.worldconfigmanager.core.protocol.WcmProtocol;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The two WCM play-phase payloads. Each carries one JSON protocol message; size limits are enforced by the codec and
 * again by the protocol handler.
 */
public final class WcmPayloads {
	private WcmPayloads() {
	}

	/** Client to server message. */
	public record Request(String json) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldconfigmanager", "request"));
		public static final StreamCodec<ByteBuf, Request> CODEC =
			ByteBufCodecs.stringUtf8(WcmProtocol.MAX_C2S_BYTES).map(Request::new, Request::json);

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/** Server to client message. */
	public record Response(String json) implements CustomPacketPayload {
		public static final Type<Response> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldconfigmanager", "response"));
		public static final StreamCodec<ByteBuf, Response> CODEC =
			ByteBufCodecs.stringUtf8(WcmProtocol.MAX_S2C_BYTES).map(Response::new, Response::json);

		@Override
		public Type<Response> type() {
			return TYPE;
		}
	}

	/** Registers both payload types. Must run once during mod initialization on both sides. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().registerLarge(Request.TYPE, Request.CODEC, WcmProtocol.MAX_C2S_BYTES + 16);
		PayloadTypeRegistry.clientboundPlay().registerLarge(Response.TYPE, Response.CODEC, WcmProtocol.MAX_S2C_BYTES + 16);
	}
}
