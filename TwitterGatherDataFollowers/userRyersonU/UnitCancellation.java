package TwitterGatherDataFollowers.userRyersonU;

/**
 * Cooperative cancellation of the work unit the current thread is executing.
 *
 * {@link NodeWorkRunner} registers the assignment a lane is running; unit code
 * calls {@link #check()} in its loops. When the coordinator revokes the
 * execution (a drain, a crash, the phase failing) the next check throws
 * {@link Revoked}, so a revoked unit stops within one loop iteration instead
 * of running to completion, and the lane is free again at once. Outside a
 * unit (no registered assignment) a check does nothing.
 */
final class UnitCancellation
{
	/** Thrown by {@link #check()} when the running execution was revoked. */
	static final class Revoked extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		Revoked(String reason)
		{
			super(reason == null ? "The execution was revoked." : "The execution was revoked: " + reason);
		}
	}

	private static final ThreadLocal<WorkDispatcher.Assignment> CURRENT =
			new ThreadLocal<WorkDispatcher.Assignment>();

	private UnitCancellation()
	{
	}

	static void enter(WorkDispatcher.Assignment assignment)
	{
		CURRENT.set(assignment);
	}

	static void exit()
	{
		CURRENT.remove();
	}

	static WorkDispatcher.Assignment current()
	{
		return CURRENT.get();
	}

	/** Throws {@link Revoked} when the current thread's execution was revoked. */
	static void check()
	{
		WorkDispatcher.Assignment assignment = CURRENT.get();
		if (assignment != null && assignment.isRevoked())
		{
			throw new Revoked(assignment.getRevokedReason());
		}
	}

	static boolean isRevoked()
	{
		WorkDispatcher.Assignment assignment = CURRENT.get();
		return assignment != null && assignment.isRevoked();
	}
}
