Water {
	classvar waternotes;
	var <server, <outbus, <group, <srcsynth, <routesynth, <barksynth, <srcBus, <routeBus, <voiceBus, <nproxy;
	var <fadeTime, <thisrate, <thisdexMax, <thisfshift, <thisbw, <thisfb, <thisamp;
	var wfshift, shiftEnv, <wtask, udtask, twait, data;
	
	*initClass {

		waternotes = CtkProtoNotes(
						
			SynthDef(\watergrainFM, {arg out, grainPeriod, grainMinFreq, grainMaxFreq, grainAmp, dexMaxin, ratein, rateFMrate=0.2, rateFMdepth=0.05, ampfluxrate=0.2, gate=1;
				var trig, fluxcntl, rate, dexMax, ampcntl, grains, freq, trigger;
				SeedMaster.ugen;
				trig = Dust.kr(ampfluxrate);
				fluxcntl = LagUD.kr(
					Trig.kr(trig, SinOsc.kr(30.reciprocal,0, 2.5, 5) ),
					3, 5 );
				//Poll.kr(Impulse.kr(0.5), fluxcntl, "flux");
				rate = In.kr(ratein);
				rate = rate * (1-(0.5*fluxcntl)); // fluxtrig makes the rate slide down to 0.5 of it's value
				//rate = rate; // + LFNoise1.kr(Lag.kr(rateFMrate, 2), Lag.kr(rateFMdepth,2)*rate );
				dexMax = In.kr(dexMaxin);
				trigger = Dust.ar( rate );
				freq = TRand.ar(grainMinFreq, grainMaxFreq, trigger);
				ampcntl = grainAmp * (1-fluxcntl);
				grains = GrainFM.ar(1, trigger, grainPeriod, freq, freq * 0.77, 
					LFNoise1.kr.range(4, dexMax ), 0, -1, mul: ampcntl);
				Out.ar(out, grains);
				}),
		
			SynthDef(\waterbark, {arg out = 0, inbus, bwin, bwModRate=0.1, bwModDepth=0.05, fbin, fshift=0.5, gate = 1, onset=5, fadeOut=8, mulin;
				var env, src, deltimes, ampvals, delamt, bdelays, fb, bw, mul;
				SeedMaster.ugen;
				env = EnvGen.kr(Env([0,1,0], [onset, fadeOut], \sin, 1), gate, doneAction:2);
				src = In.ar(inbus, 1);
				deltimes = Array.fill( 25, 0.0 );
				ampvals = Array.fill( 25, 1.0 );
								
				delamt = 25.collect({ SinOsc.kr(200.reciprocal, rrand(0.0, 2pi), 0.7, 0.75) });
				25.do{arg i;
					deltimes[i] = delamt[i];
					ampvals[i] = rrand(0.4, 1.1);
					};
					
				fb = In.kr(fbin);
				bw = In.kr(bwin);
				bw = bw + LFNoise1.kr( Lag.kr(bwModRate,2), Lag.kr(bwModDepth,2)*bw );
				mul = In.kr(mulin);
				
				bdelays = Limiter.ar( // limiter for feedback
					BarkDelay.ar(src, deltimes, fb, ampvals, 1.5, bw, fshift),
					0.9, 0.2);
				Out.ar(out, (bdelays.sum * env * mul));
				}),
				
			SynthDef( \waterrouter, { arg dirout=0, voiceout, inbus, voicemix=0, fade=12;
				var src, voicefade;
				SeedMaster.ugen;
				src = In.ar(inbus, 1);
				voicefade = Lag.kr( voicemix, fade );
				Out.ar( dirout, src * (1 - voicefade) );
				Out.ar( voiceout, src * voicefade ); 
				})
		)
	
	}
	
	*new { | server, outbus, data, maxwait=5, amp = 0.6, fadeTime=6, onset=5 |
		^super.newCopyArgs( server, outbus ).init( outbus, data, maxwait, amp, fadeTime, onset, server )
	}
	
	init { | outbus, argdata, argwait, amp, argFadeTime, onset, argserver |
	
		server = argserver ?? {Server.default};
		group = CtkGroup.play( server:server, addAction:\head );
		srcBus = CtkAudio.new( server: server );
		routeBus = CtkAudio.new( server: server );
		voiceBus = CtkAudio.new( server: server );
		fadeTime = argFadeTime;
		data = argdata;
		twait = argwait;
		//nproxy.parentGroup_(group.nodeID);
		
			// nodeproxy mapping: rate, dexMax, fshift, bw, fb, amp
		thisrate = 70;
		thisdexMax =  2;
		thisbw = 0.1;
		thisfb = 0.4;
		thisamp = amp;
		
		wfshift = 0.9;

		nproxy = NodeProxy.new( server, \control, 5, [[thisrate, thisdexMax, thisbw, thisfb, thisamp]] );
		
		srcsynth = waternotes[\watergrainFM].new(addAction: \head, target: group)
			.randSeed_(SeedMaster.seed)
			.out_(srcBus).grainPeriod_(0.001)
			.ratein_(nproxy.index).dexMaxin_(nproxy.index+1)
			.grainMinFreq_(100).grainMaxFreq_(4900).grainAmp_(-9.dbamp);
		
		barksynth = waternotes[\waterbark].new(addAction: \after, target: srcsynth).out_(routeBus).inbus_(srcBus)
			.randSeed_(SeedMaster.seed)
			.bwin_(nproxy.index+2).fbin_(nproxy.index+3).mulin_(nproxy.index+4)
			.fshift_(wfshift).onset_(onset);

		routesynth = waternotes[\waterrouter].new(addAction: \tail, target: group)
			.randSeed_(SeedMaster.seed)
			.inbus_( routeBus )
			.dirout_( outbus )
			.voiceout_( voiceBus );
			
			// the water's database task
		wtask = //{
//			data.size.postln;
		Task({
				(data.size - 1).do({arg i;
					var name, age, fmrate, mxfrq, mnfrq, fmdepth, bwmodrate, bwmoddepth;

					//"*********".postln;				
					name = data[i][1]; 		// the name, already converted to relative occurences
					age = data[i][0]/63.2465; 	// age, as a ratio of the average age of THE DEAD						
						// scaling it by it's own max value, extending each name to top partial
					//name = name / name.maxItem; 
						// scaling the name by the max possible value for all names, name may not get to topPartial
					name = name / 11.96;	
					
						///// MESS WITH THESE RANGES //////
						
					//fmrate = name.mean.linlin(0,1.0,0.1,0.7); // picks a letter of the name to use for the fm rate
					//fmdepth = age % 1; //make sure it doesn't go over 1
					//bwmodrate = name.choose.linlin(0.0,1 ,10,0.1);
					bwmodrate = name[1].linlin(0.0,1 ,6,0.1);
					bwmoddepth = age.linlin(0.4,1.25,0.05, 0.65);
					
					mxfrq = name.choose.linlin(0.0,1, 200, rrand(2000,5000));
					srcsynth.grainMaxFreq_(mxfrq);
					mnfrq = name.choose.linlin(0.0,1, 50, 800);
					srcsynth.grainMinFreq_(mnfrq);
					
					
					//srcsynth.rateFMrate_(fmrate);
					//srcsynth.rateFMdepth_(fmdepth);
					barksynth.bwModRate_(bwmodrate);
					barksynth.bwModDepth_(bwmoddepth);
					
					//"mnfrq ".post; mnfrq.postln;
					//"mxfrq ".post; mxfrq.postln;
					//"fmrate ".post; fmrate.postln;
					//"fmdepth ".post; fmdepth.postln;
					//"bwmodrate ".post; bwmodrate.postln;
					//"bwmoddepth ".post; bwmoddepth.postln;
					
					name.maxItem.linlin(0, 1, 1, rrand(2.0, twait)).wait;
				});	
		});
//		};
	
		udtask = Task({
				| starttime |
				var step;				
				step = 0.2;
				inf.do({ 
					var now, newshift;
					now = thisThread.beats - starttime;
					//"now: ".post; now.postln;
					newshift = shiftEnv[ now ];
					barksynth.fshift_(newshift);
					wfshift = newshift;
					//"fshift = ".post; wfshift.postln;
					step.wait;
					})			
		});
		
		CmdPeriod.add({ group.deepFree })
	}
	
	play {
		SystemClock.sched( 0.1, {
			"Src".postln;
			srcsynth.play(group);
			});
		SystemClock.sched( 0.4, {
			"Bark".postln;
			barksynth.play(group);
			});
		SystemClock.sched(0.7, {
			"Route".postln;
			routesynth.play(group);
			});
		SystemClock.sched(1.2, {
			"wtask".postln;
			wtask.play;
			});

	}
		
	free { | releaseTime=3 |
		barksynth.fadeOut_(releaseTime);
		barksynth.release;
		SystemClock.sched( releaseTime, { 
			wtask.stop.reset;
			udtask.stop.reset;
			group.deepFree; srcBus.free; routeBus.free; voiceBus.free; nproxy.clear; "water freed".postln;
			//group = nil; 
			nproxy = nil;
			wtask = udtask = nil
		});
	}
	
	killNow {
		barksynth.free;
		wtask.stop.reset; udtask.stop.reset;
		group.deepFree; srcBus.free; routeBus.free; voiceBus.free; nproxy.clear; "water killed".postln;
		wtask = udtask = nil;
		group = nproxy = nil;
	}

	updateState { | transitionTime, rate, dexMax, freqshift, bw, fb, amp |
		//udtask.clock.clear; // clear scheduled stop messages so they don't come back after this new call
		udtask.stop.reset; // in case the task is already playing
		nproxy.fadeTime = transitionTime;
		nproxy.source = [ rate, dexMax, bw, fb, amp ];
		shiftEnv = Env( [wfshift , freqshift],[transitionTime], [0] );
		SystemClock.sched( 0.5, { udtask.play } );
		SystemClock.sched( transitionTime+1, { udtask.stop.reset; "updating is complete".postln } );
	}
		
	sendToVoice { |fadein=8| routesynth.fade_(fadein).voicemix_(1) }
	
	removeVoice { |fadeout=8| routesynth.fade_(fadeout).voicemix_(0) }
	
	rateFMrate_{ |argrateFMrate| srcsynth.rateFMrate_(argrateFMrate) }
	
	rateFMdepth_{ |argrateFMdepth| srcsynth.rateFMdepth_(argrateFMdepth) }

	bwModRate_{ |argbwModRate| barksynth.bwModRate_(argbwModRate) }

	bwModDepth_{ |argbwModDepth| barksynth.bwModDepth_(argbwModDepth) }	
	twait_{ |argtwait| twait = argtwait }
	
	minFreq_{ |argmnfrq| srcsynth.grainMinFreq_(argmnfrq) }
	
	maxFreq_{ |argmxfrq| srcsynth.grainMaxFreq_(argmxfrq) }
	
	ampfluxrate_{ |argaflux| srcsynth.ampfluxrate_(argaflux) }
}


/*
n= CtkNoteObject(

	SynthDef( \test, { arg out=0, freqin, gate=1;
		var env, src;
		Poll.kr( Impulse.kr(1), freqin, "freqin");
		//env = EnvGen.kr( Env( [0,1,0],[0.5, 0.5], \sin, 1 ), gate, doneAction: 2 );
		src = SinOsc.ar( freqin,
			//EnvGen.kr( Control.names(\freq).kr(Env.newClear(2).asArray));
			0, 0.4);
		Out.ar( out,  src * 0.2);
		})
	
	)

a = n.new().play
a.map( 0, \freqin, c.index )


c = NodeProxy.control(s, 1);
c.source = 1000;
c.fadeTime = 10
*/