WatchClipping : UGen {
	*ar {
		| in, id, clipLevel=1 |
		var clip = in.abs > clipLevel;
		SendTrig.ar( Trig1.kr( clip, 1), in, id, PulseCount.ar( clip ) )
	}
}