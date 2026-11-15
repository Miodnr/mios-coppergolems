package dev.mioscoppergolems.habour.route;

/** What a route is doing right now, for the route screen. The ordinal is sent to the client. */
public enum Status {
	PAUSED,
	WAITING_TIMER,
	WAITING_REDSTONE,
	FINISHED,
	NO_DESTINATION,
	NO_STOCK,
	NO_CAPTAIN,
	NO_BOAT,
	NO_BERTH,
	LOADING,
	SAILING,
	NO_DOCKHAND,
	NO_CONDUCTOR,
	NO_TRAIN,
	NO_TRACK;

	public static Status byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : PAUSED;
	}
}
