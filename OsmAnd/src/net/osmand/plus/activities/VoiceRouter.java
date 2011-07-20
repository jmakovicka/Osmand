package net.osmand.plus.activities;

import net.osmand.plus.activities.RoutingHelper.RouteDirectionInfo;
import net.osmand.plus.activities.RoutingHelper.TurnType;
import net.osmand.plus.voice.AbstractPrologCommandPlayer;
import net.osmand.plus.voice.CommandBuilder;
import net.osmand.plus.voice.CommandPlayer;
import android.app.Activity;
import android.location.Location;


public class VoiceRouter {
	// 0 - unknown, 1 - notify prepare, 2 - notify to turn after , 3 - notify to turn
	private final int STATUS_UNKNOWN = 0;
	private final int STATUS_3000_PREPARE = 1;
	private final int STATUS_800_PREPARE = 2;
	private final int STATUS_200_TURN = 3;
	private final int STATUS_TURN = 4;
	private final int STATUS_TOLD = 5;
	
	private final RoutingHelper router;
	private boolean mute = false;
	private CommandPlayer player;
	
	private int currentDirection = 0;
 
	private int currentStatus = STATUS_UNKNOWN;
	
	// default speed to have comfortable announcements (if actual speed is higher than it would be problem)
	// Speed in m/s 
	protected float DEFAULT_SPEED = 12;
		
	protected int PREPARE_LONG_DISTANCE_ST = 2500;
	protected int PREPARE_LONG_DISTANCE_END = 3200;
	
	protected int PREPARE_DISTANCE = 0;
	protected int PREPARE_DISTANCE_END = 0;
	protected int TURN_IN_DISTANCE = 0;
	protected int TURN_IN_DISTANCE_END = 0;
	protected int TURN_DISTANCE = 0;


	public VoiceRouter(RoutingHelper router, CommandPlayer player) {
		this.router = router;
		this.player = player;
		updateAppMode();
	}
	
	public void setPlayer(CommandPlayer player) {
		this.player = player;
	}
	
	
	public void setMute(boolean mute) {
		this.mute = mute;
	}
	
	public boolean isMute() {
		return mute;
	}
	
	
	protected CommandBuilder getNewCommandPlayerToPlay(){
		if(player == null || mute){
			return null;
		}
		return player.newCommandBuilder();
	}
	
	
	
	public void updateAppMode(){
		// Else consider default time 
		if(router.getAppMode() == ApplicationMode.PEDESTRIAN){
			// prepare distance needed ?
			PREPARE_DISTANCE = 320; // 160 second
			PREPARE_DISTANCE_END = 200; // 75 second
			TURN_IN_DISTANCE = 100; // 50 seconds
			TURN_IN_DISTANCE_END = 70; // 35 seconds
			TURN_DISTANCE = 25; // 12 seconds
			DEFAULT_SPEED = 2f;
		} else if(router.getAppMode() == ApplicationMode.BICYCLE){
			PREPARE_DISTANCE = 530; // 100 seconds
			PREPARE_DISTANCE_END = 370; // 70 seconds
			TURN_IN_DISTANCE = 230; // 40 seconds
			TURN_IN_DISTANCE_END = 90; // 16 seconds
			TURN_DISTANCE = 45; // 9 seconds  
			DEFAULT_SPEED = 5;
		} else {
			PREPARE_DISTANCE = 730; // 60 seconds
			PREPARE_DISTANCE_END = 530; // 45 seconds
			TURN_IN_DISTANCE = 330; // 25 seconds
			TURN_IN_DISTANCE_END = 160; // 14 seconds
			TURN_DISTANCE = 60; // 5 seconds 
			DEFAULT_SPEED = 12;
		}
	}
	
	
	private boolean isDistanceLess(float currentSpeed, double dist, double etalon){
		if(dist < etalon || (dist / currentSpeed < etalon / DEFAULT_SPEED)){
			return true;
		}
		return false;
	}
	
	
	private void nextStatusAfter(int previousStatus){
//		if(previousStatus == STATUS_TURN){
//			this.currentStatus = STATUS_TOLD;
//		} else if(previousStatus == STATUS_200_TURN){
//			this.currentStatus = STATUS_TURN;
//		} else if(previousStatus == STATUS_800_PREPARE){
//			this.currentStatus = STATUS_200_TURN;
//		} else if(previousStatus == STATUS_3000_PREPARE){
//			this.currentStatus = STATUS_800_PREPARE;
//		} else
		if(previousStatus != STATUS_TOLD){
			this.currentStatus = previousStatus + 1;
		} else {
			this.currentStatus = previousStatus;
		}
	}
	
	private boolean statusNotPassed(int statusToCheck){
		return currentStatus <= statusToCheck;
	}
	
	/**
	 * Updates status of voice guidance 
	 * @param currentLocation 
	 */
	protected void updateStatus(Location currentLocation){
		// directly after turn (go - ahead dist)
		// < 800m prepare
		// < 200m turn in
		// < 50m turn
		float speed = DEFAULT_SPEED;
		if(currentLocation != null && currentLocation.hasSpeed()){
			speed = Math.max(currentLocation.getSpeed(), speed);
		}
		RouteDirectionInfo next = router.getNextRouteDirectionInfo();
		int dist = router.getDistanceToNextRouteDirection();
		
		// if routing is changed update status to unknown 
		if(currentDirection != router.currentDirectionInfo){
			currentDirection = router.currentDirectionInfo;
			currentStatus = STATUS_UNKNOWN;
		}
		
		
		// the last turn say 
		if(next == null || next.distance == 0) {
			if(currentStatus == STATUS_UNKNOWN && currentDirection > 0){
				CommandBuilder play = getNewCommandPlayerToPlay();
				if(play != null){
					play.goAhead(router.getLeftDistance()).andArriveAtDestination().play();
				}
				currentStatus = STATUS_TOLD;
			}
			return;
		}
		if(dist == 0 || currentStatus == STATUS_TOLD){
			// nothing said possibly that's wrong case we should say before that
			// however it should be checked manually ?
			return;
		}
		
		
		RouteDirectionInfo nextNext = router.getNextNextRouteDirectionInfo();
		if(statusNotPassed(STATUS_TURN) && isDistanceLess(speed, dist, TURN_DISTANCE)){
			if(/*isDistanceLess(speed, next.distance, TURN_DISTANCE) || */next.distance < TURN_IN_DISTANCE_END) {
				playMakeTurnRightNow(next, nextNext);
			} else {
				playMakeTurnRightNow(next, null);
			}
			nextStatusAfter(STATUS_TURN);
		} else if(statusNotPassed(STATUS_200_TURN) && isDistanceLess(speed, dist, TURN_IN_DISTANCE)){
			if (dist >= TURN_IN_DISTANCE_END) {
				if(isDistanceLess(speed, next.distance, TURN_DISTANCE) || next.distance < TURN_IN_DISTANCE_END) {
					playMakeTurnInShortDistance(next, dist, nextNext);
				} else {
					playMakeTurnInShortDistance(next, dist, null);
				}
			}
			nextStatusAfter(STATUS_200_TURN);
		} else if (statusNotPassed(STATUS_800_PREPARE) && isDistanceLess(speed, dist, PREPARE_DISTANCE)) {
			if (dist >= PREPARE_DISTANCE_END) {
				playPrepareLongDistanceTurn(next, dist);
			}
			nextStatusAfter(STATUS_800_PREPARE);
		} else if(statusNotPassed(STATUS_UNKNOWN)){
			if (dist >= PREPARE_DISTANCE * 1.3f) {
				playGoAhead(dist);
			}
			if (dist >= PREPARE_LONG_DISTANCE_END * 1.5f) {
				nextStatusAfter(STATUS_UNKNOWN);
			} else {
				nextStatusAfter(STATUS_3000_PREPARE);
			}
		} else if(statusNotPassed(STATUS_3000_PREPARE) && isDistanceLess(speed, dist, PREPARE_LONG_DISTANCE_END)){
			if (dist >= PREPARE_LONG_DISTANCE_ST) {
				playPrepareLongDistanceTurn(next, dist);
			} 
			nextStatusAfter(STATUS_3000_PREPARE);
		}
	}

	private void playGoAhead(int dist) {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if(play != null){
			play.goAhead(dist).play();
		}
	}

	private void playPrepareLongDistanceTurn(RouteDirectionInfo next, int dist) {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if(play != null){
			String tParam = getTurnType(next.turnType);
			if(tParam != null){
				play.prepareTurn(tParam, dist).play();
			} else if(next.turnType.isRoundAbout()){
				play.prepareRoundAbout(dist).play();
			} else if(next.turnType.getValue().equals(TurnType.TU)){
				play.prepareMakeUT(dist).play();
			} 
		}
	}

	private void playMakeTurnInShortDistance(RouteDirectionInfo next, int dist, RouteDirectionInfo pronounceNextNext) {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if (play != null) {
			String tParam = getTurnType(next.turnType);
			boolean isPlay = true;
			if (tParam != null) {
				play.turn(tParam, dist);
			} else if (next.turnType.isRoundAbout()) {
				play.roundAbout(dist,  next.turnType.getTurnAngle(), next.turnType.getExitOut());
			} else if (next.turnType.getValue().equals(TurnType.TU)) {
				play.makeUT(dist);
			} else {
				isPlay = false;
			}
			// small preparation to next after next
			if (pronounceNextNext != null) {
				TurnType t = pronounceNextNext.turnType;
				isPlay = true;
				if (next.turnType.getValue().equals(TurnType.C) && 
						!TurnType.C.equals(t.getValue())) {
					play.goAhead(dist);
				}
				if (TurnType.TL.equals(t.getValue()) || TurnType.TSHL.equals(t.getValue()) || TurnType.TSLL.equals(t.getValue())
						|| TurnType.TU.equals(t.getValue())) {
					play.then().bearLeft();
				} else if (TurnType.TR.equals(t.getValue()) || TurnType.TSHR.equals(t.getValue()) || TurnType.TSLR.equals(t.getValue())) {
					play.then().bearRight();
				}
			}
			if(isPlay){
				play.play();
			}
		}
	}
	
	

	private void playMakeTurnRightNow(RouteDirectionInfo next, RouteDirectionInfo nextNext) {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if(play != null){
			String tParam = getTurnType(next.turnType);
			boolean isplay = true;
			if(tParam != null){
				play.turn(tParam);
			} else if(next.turnType.isRoundAbout()){
				play.roundAbout(next.turnType.getTurnAngle(), next.turnType.getExitOut());
			} else if(next.turnType.getValue().equals(TurnType.TU)){
				play.makeUT();
				// do not say it
//				} else if(next.turnType.getValue().equals(TurnType.C)){
//					play.goAhead();
			} else {
				isplay = false;
			}
			// add turn after next
			if (nextNext != null) {
				String t2Param = getTurnType(nextNext.turnType);
				if (t2Param != null) {
					if(isplay) { play.then(); }
					play.turn(t2Param, next.distance);
				} else if (nextNext.turnType.isRoundAbout()) {
					if(isplay) { play.then(); }
					play.roundAbout(next.distance, nextNext.turnType.getTurnAngle(), nextNext.turnType.getExitOut());
				} else if (nextNext.turnType.getValue().equals(TurnType.TU)) {
					if(isplay) { play.then(); }
					play.makeUT(next.distance);
				}
				isplay = true;
			}
			if(isplay){
				play.play();
			}
		}
	}
	
	private String getTurnType(TurnType t){
		if(TurnType.TL.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_LEFT;
		} else if(TurnType.TSHL.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_LEFT_SH;
		} else if(TurnType.TSLL.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_LEFT_SL;
		} else if(TurnType.TR.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_RIGHT;
		} else if(TurnType.TSHR.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_RIGHT_SH;
		} else if(TurnType.TSLR.equals(t.getValue())){
			return AbstractPrologCommandPlayer.A_RIGHT_SL;
		}
		return null;
	}

	public void newRouteIsCalculated(boolean updateRoute) {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if (play != null) {
			if (updateRoute) {
				play.routeRecalculated(router.getLeftDistance()).play();
			} else {
				play.newRouteCalculated(router.getLeftDistance()).play();
			}
		}
		currentDirection = router.currentDirectionInfo;
		currentStatus = 0;
	}

	public void arrivedDestinationPoint() {
		CommandBuilder play = getNewCommandPlayerToPlay();
		if(play != null){
			play.arrivedAtDestination().play();
		}
	}

	public void onActivityInit(Activity activity) {
		if (player != null) {
			player.onActivityInit(activity);
		}
	}

	public void onActivityStop(Activity activity) {
		if (player != null) {
			player.onActvitiyStop(activity);
		}
	}

}
