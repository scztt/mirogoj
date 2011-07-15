+ CtkGroup {
	asTarget { ^this }
	asNodeID { ^node }
	nodeID { ^node }
	isPlaying{ ^isGroupPlaying }
	isPlaying_{
		| playing |
		if( playing && isGroupPlaying.not, {
			this.play;
		})
	}
	
	isRunning{ ^isGroupPlaying }
}