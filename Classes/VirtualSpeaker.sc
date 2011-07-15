VirtualSpeaker {
	classvar <scale=0.25, <>speakerEffectsFunc;
	var server, group;
	var <amp=1, <x=0, <y=0, <angle=0, <>inBus, <>outBus, <>listenerPositionBus;
	var <synth;
	
	*synthDef {
		^SynthDef( \virtSpeaker, {
			| out, in, x, y, angle, amp=1, listenerIn, t_debug |
			var inSig, sig, bSig, listenerX, listenerY, 
				relativeAngle, speakerAngle, listenerAngle, 
				dist, distDelay, angleAmp, distAmp, scalar;
			SeedMaster.ugen;
			scalar = 1;
			x = x/scalar; y = y/scalar;
			inSig = InFeedback.ar( in, 1 );
			#listenerX, listenerY, listenerAngle = In.kr( listenerIn, 3 );
			listenerX = listenerX/scalar; listenerY = listenerY/scalar;
			
			// angle of the listener relative to the speaker
			speakerAngle = this.calcSpeakerAngle( listenerX, listenerY, x, y, angle );
		
			// angle of the speaker relative to the listener
			relativeAngle = this.calcRelativeAngle( listenerX, listenerY, x, y, listenerAngle );
			
			dist = hypot(x-listenerX, y-listenerY).abs;
			distDelay = (dist/340);
			
			angleAmp = this.calcAmpFromAngle( speakerAngle );
			//angleAmp = 1;
			//distAmp = 1/(dist.max(1).pow(1.5));
			
			sig = inSig*angleAmp; //*distAmp;
			//sig = BHiShelf.ar( sig, 4000, 6, -10*(dist/50) );
			//sig = DelayC.ar( sig, 0.6, distDelay );
			sig = DistAttenuate.ar(sig, dist);
						
			bSig = PanB.ar( sig, relativeAngle );
			Out.ar( out, bSig );
		})
	}
	
	*applySpeakerEffects {
		| sig |
		if( this.speakerEffectsFunc.notNil, {
			^this.speakerEffectsFunc.value( sig )
		},{
			^sig
		})
	}
	
	*calcSpeakerAngle {
		| listenX, listenY, speakerX, speakerY, speakerAngle |
		^((atan2( listenX-speakerX, speakerY-listenY )/pi/2) - speakerAngle).mod(1);
	}
	
	*calcRelativeAngle {
		| listenX, listenY, speakerX, speakerY, listenerAngle |
		^( ((listenX@listenY) - (speakerX@speakerY)).theta - (pi/2) - (listenerAngle*pi*2) )/pi;
	}
	
	*calcAmpFromAngle {
		| rot |
		rot = 1 - (((rot*2)-1).abs).pow(2.2); 
		^(1 - (rot*0.78));
	}
	
	*new {
		| server, group, x=0, y=0, angle=0, amp=1, inBus, outBus, listenerPositionBus |
		^super.newCopyArgs( server, group, amp, x, y, angle, 
			inBus, outBus, listenerPositionBus ).init
	}
	
	init {
		synth = CtkNoteObject( this.class.synthDef, server:server ).new( target:group, addAction:\head )
			.out_( outBus.bus )
			.in_( inBus.bus )
			.listenerIn_( listenerPositionBus.bus );
	}

	play {
		if( synth.isPlaying.not, {
			synth.x_(x)
				.y_(y)
				.angle_(angle)
				.amp_(amp)
				.play(group);
			})
	}
	
	free {
		if( synth.isPlaying, {
			synth.free;
		})
	}
		
	x_{
		| val |
		synth.x = x = val;
	}

	y_{
		| val |
		synth.y = y = val;
	}
	
	angle_{
		| val |
		synth.angle = angle = val;
	}
	
	amp_{
		| val |
		synth.amp = amp = val;
	}
	
	serialize {
		var dict = Dictionary.new();
		dict.add( \x -> x );
		dict.add( \y -> y );
		dict.add( \angle -> angle );
		dict.add( \amp -> amp );
		^dict 
	}
	
	busNum {
		^inBus.bus
		
	}
	
	test {
		| dur=4 |
		var test ;
		test = { WhiteNoise.ar( 1 ) }.play( GroupManager.get(server).synths, inBus.bus, 0.1, \addToHead );
		{ test.free }.defer(dur);
	}
	
	out {
		| sig |
		Out.ar( inBus.bus, sig );
	}
}
