+ NodeProxy {
	tempSource {
		| newSource, args, duration, tempFadeTime=nil |
		var tempSource, oldSource, oldFadeTime;
		if( tempFadeTime.notNil, {  oldFadeTime = this.fadeTime; this.fadeTime = tempFadeTime });

		oldSource = this.at(0);
		this.put( nil, newSource, 0, args );
		
		this.fadeTime = oldFadeTime;
		
		SystemClock.sched( duration, {
			if( this.at(0) == newSource, {
				"putting old source back".postln;
				if( tempFadeTime.notNil, {  oldFadeTime = this.fadeTime; this.fadeTime = tempFadeTime });
				this.source = oldSource;
				this.fadeTime = oldFadeTime;
			})
		})
	}
}