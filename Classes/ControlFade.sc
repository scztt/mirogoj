ControlFade {
	*kr {
		| x, fadeTime=1 |
		var last = LastValue.kr(x, 0.0001);
		fadeTime = last.absdif(x).max(0.00001) /fadeTime;
		^Slew.kr( x, fadeTime, fadeTime+0.0000001 );
	}
}